package com.nac.auth.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nac.auth.dto.NetApplyReq;
import com.nac.auth.dto.NetInterface;
import com.nac.auth.dto.NetInterface.IpInfo;
import com.nac.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务器本机网络接口管理：读取各网卡状态/速率/地址/网关/DNS，并通过受限 sudo 应用修改。
 * 仅解析与下发，不持久化（修改为即时生效，重启后由系统网络服务还原）。
 */
@Slf4j
@Service
public class NetworkService {

    private static final String IP = "/usr/sbin/ip";
    private static final String RESOLVECTL = "/usr/bin/resolvectl";
    private static final Pattern LINK_DNS = Pattern.compile("Link\\s+\\d+\\s+\\(([^)]+)\\):\\s*(.*)");

    public List<NetInterface> listInterfaces() {
        Map<String, String> gwByDev = parseGateways();
        String globalGw = gwByDev.remove("__global__");
        Map<String, List<String>> dnsByDev = parseDns();
        List<String> globalDns = dnsByDev.remove("__global__");

        List<NetInterface> result = new ArrayList<>();
        try {
            String json = run(IP, "-json", "addr", "show");
            JSONArray arr = JSON.parseArray(json);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String name = o.getString("ifname");
                if (skip(name)) continue;
                NetInterface ni = new NetInterface();
                ni.setName(name);
                JSONArray flags = o.getJSONArray("flags");
                boolean up = false;
                if (flags != null) for (int j = 0; j < flags.size(); j++) {
                    if ("UP".equals(flags.getString(j))) { up = true; break; }
                }
                ni.setUp(up);
                ni.setOperState(o.getString("operstate"));
                ni.setLinkUp("UP".equals(ni.getOperState()));
                ni.setMtu(o.getIntValue("mtu"));
                ni.setSpeedMbps(readSpeed(name));

                JSONArray addrInfo = o.getJSONArray("addr_info");
                if (addrInfo != null) {
                    for (int j = 0; j < addrInfo.size(); j++) {
                        JSONObject a = addrInfo.getJSONObject(j);
                        String family = a.getString("family");
                        String scope = a.getString("scope");
                        String local = a.getString("local");
                        int prefix = a.getIntValue("prefixlen");
                        if ("inet".equals(family) && ni.getIpv4() == null && !"host".equals(scope)) {
                            IpInfo ip = new IpInfo();
                            ip.setAddress(local); ip.setPrefix(prefix); ip.setMask(prefixToMask(prefix));
                            ni.setIpv4(ip);
                        } else if ("inet6".equals(family) && ni.getIpv6() == null && "global".equals(scope)) {
                            IpInfo ip = new IpInfo();
                            ip.setAddress(local); ip.setPrefix(prefix);
                            ni.setIpv6(ip);
                        }
                    }
                }
                ni.setGateway(gwByDev.getOrDefault(name, globalGw));
                // 按地址族拆分 DNS：v4 表只显示 IPv4 服务器，v6 表只显示 IPv6 服务器，没有则为空
                List<String> dns = dnsByDev.get(name);
                if (dns != null && !dns.isEmpty()) {
                    String v4a = null, v4b = null, v6a = null, v6b = null;
                    for (String s : dns) {
                        boolean isV6 = s.indexOf(':') >= 0;
                        if (isV6) {
                            if (v6a == null) v6a = s; else if (v6b == null) v6b = s;
                        } else {
                            if (v4a == null) v4a = s; else if (v4b == null) v4b = s;
                        }
                    }
                    ni.setDns1(v4a); ni.setDns2(v4b);
                    ni.setIpv6Dns1(v6a); ni.setIpv6Dns2(v6b);
                }
                result.add(ni);
            }
        } catch (Exception e) {
            log.error("读取网络接口失败", e);
            throw new BusinessException(400, "读取网络接口失败: " + e.getMessage());
        }
        return result;
    }

    /** 应用修改：mtu / 启停 / 地址 / 网关 / DNS，逐项执行，失败即抛错。 */
    public void apply(NetApplyReq req) {
        if (req.getName() == null || req.getName().isBlank() || req.getName().matches(".*\\s.*"))
            throw new BusinessException(400, "接口名非法");
        if (skip(req.getName())) throw new BusinessException(400, "不允许修改该接口");
        String dev = req.getName();
        try {
            if (req.getMtu() != null)
                sudo(IP, "link", "set", "dev", dev, "mtu", String.valueOf(req.getMtu()));
            if (req.getUp() != null)
                sudo(IP, "link", "set", "dev", dev, req.getUp() ? "up" : "down");
            if (req.getIpv4Address() != null && !req.getIpv4Address().isBlank()) {
                int p = req.getIpv4Prefix() == null ? 24 : req.getIpv4Prefix();
                sudo(IP, "addr", "flush", "dev", dev, "scope", "global");
                sudo(IP, "addr", "add", req.getIpv4Address() + "/" + p, "dev", dev);
                sudo(IP, "link", "set", "dev", dev, "up");
            }
            if (req.getIpv6Address() != null && !req.getIpv6Address().isBlank()) {
                int p = req.getIpv6Prefix() == null ? 64 : req.getIpv6Prefix();
                sudo(IP, "-6", "addr", "add", req.getIpv6Address() + "/" + p, "dev", dev);
            }
            if (req.getGateway() != null && !req.getGateway().isBlank())
                sudo(IP, "route", "replace", "default", "via", req.getGateway(), "dev", dev);
            if (req.getDns1() != null && !req.getDns1().isBlank()) {
                List<String> cmd = new ArrayList<>(List.of(RESOLVECTL, "dns", dev, req.getDns1()));
                if (req.getDns2() != null && !req.getDns2().isBlank()) cmd.add(req.getDns2());
                sudo(cmd.toArray(new String[0]));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("应用网络配置失败 dev={}", dev, e);
            throw new BusinessException(400, "应用网络配置失败: " + e.getMessage());
        }
    }

    private Map<String, String> parseGateways() {
        Map<String, String> map = new HashMap<>();
        try {
            String json = run(IP, "-json", "route");
            JSONArray arr = JSON.parseArray(json);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String dst = o.getString("dst");
                if (dst != null && !"default".equals(dst)) continue;
                String gw = o.getString("gateway");
                String dev = o.getString("dev");
                if (gw == null || dev == null) continue;
                map.putIfAbsent(dev, gw);
                map.putIfAbsent("__global__", gw);
            }
        } catch (Exception e) {
            log.warn("解析网关失败", e);
        }
        return map;
    }

    private Map<String, List<String>> parseDns() {
        Map<String, List<String>> map = new HashMap<>();
        try {
            String out = run(RESOLVECTL, "dns");
            List<String> global = new ArrayList<>();
            for (String line : out.split("\n")) {
                Matcher m = LINK_DNS.matcher(line.trim());
                if (m.matches()) {
                    map.put(m.group(1), splitIps(m.group(2)));
                } else if (line.trim().startsWith("Global:")) {
                    String rest = line.trim().substring("Global:".length()).trim();
                    global.addAll(splitIps(rest));
                }
            }
            map.put("__global__", global);
        } catch (Exception e) {
            // 回退 /etc/resolv.conf
            List<String> ns = new ArrayList<>();
            try {
                for (String line : Files.readAllLines(Paths.get("/etc/resolv.conf"))) {
                    if (line.startsWith("nameserver")) {
                        String[] p = line.trim().split("\\s+");
                        if (p.length > 1) ns.add(p[1]);
                    }
                }
            } catch (Exception ignore) {}
            map.put("__global__", ns);
        }
        return map;
    }

    private List<String> splitIps(String s) {
        List<String> r = new ArrayList<>();
        if (s == null) return r;
        for (String t : s.trim().split("\\s+")) if (!t.isEmpty()) r.add(t);
        return r;
    }

    private long readSpeed(String name) {
        // 1) 有线网卡：/sys/class/net/<if>/speed（Mbps）
        try {
            String s = Files.readString(Paths.get("/sys/class/net", name, "speed")).trim();
            long v = Long.parseLong(s);
            if (v > 0) return v;
        } catch (Exception ignore) {}
        // 2) 无线网卡：iw dev <if> link 解析 tx bitrate（MBit/s）
        try {
            String out = run("iw", "dev", name, "link");
            Matcher m = Pattern.compile("tx bitrate:\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(out);
            if (m.find()) return (long) Double.parseDouble(m.group(1));
        } catch (Exception ignore) {}
        return 0;
    }

    private boolean skip(String name) {
        return name == null || name.equals("lo")
                || name.startsWith("veth") || name.startsWith("br-")
                || name.startsWith("docker") || name.startsWith("virbr");
    }

    private String prefixToMask(int prefix) {
        if (prefix <= 0 || prefix > 32) return "";
        long m = (0xffffffffL << (32 - prefix)) & 0xffffffffL;
        return ((m >> 24) & 255) + "." + ((m >> 16) & 255) + "." + ((m >> 8) & 255) + "." + (m & 255);
    }

    private String run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String l; while ((l = r.readLine()) != null) sb.append(l).append('\n');
        }
        p.waitFor();
        if (p.exitValue() != 0) throw new IllegalStateException(sb.toString().trim());
        return sb.toString();
    }

    private void sudo(String... cmd) throws Exception {
        List<String> full = new ArrayList<>();
        full.add("sudo"); full.add("-n");
        for (String c : cmd) full.add(c);
        Process p = new ProcessBuilder(full).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String l; while ((l = r.readLine()) != null) sb.append(l).append('\n');
        }
        p.waitFor();
        if (p.exitValue() != 0)
            throw new BusinessException(400, "命令执行失败（需特权）: " + sb.toString().trim());
    }
}
