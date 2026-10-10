package com.nac.auth.service;

import com.alibaba.fastjson2.JSON;
import com.nac.auth.dto.MonitorSnapshot;
import com.nac.auth.dto.NetInterface;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.NetworkIF;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务器监控采集（CPU/内存/网卡）+ 微服务存活探测。
 * 后台每 2s 采集一次并通过 SSE 推送给在线仪表盘；GET /snapshot 返回全量快照。
 */
@Slf4j
@Service
public class MonitorService {

    private final SystemInfo si = new SystemInfo();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "monitor-collector");
        t.setDaemon(true);
        return t;
    });
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    private volatile MonitorSnapshot latest = new MonitorSnapshot();

    private final NetworkService networkService;

    private long[] prevTicks;
    private final Map<String, long[]> prevNet = new HashMap<>(); // name -> [rx, tx]
    private long prevTs = 0L;
    private volatile Integer memFreq;
    private volatile boolean freqTried;

    /** 微服务/中间件存活探测（按本地端口）；第三列为协议，tcp 走 connect 探测，udp 走 ss -lun 监听检测 */
    private static final Object[][] SERVICES = {
            {"Nginx", 443, "tcp"}, {"Redis", 6379, "tcp"}, {"Kafka", 9092, "tcp"}, {"MySQL", 3306, "tcp"},
            {"网关 gateway", 8080, "tcp"}, {"认证 auth", 8081, "tcp"}, {"用户 user", 8082, "tcp"},
            {"RADIUS", 8083, "tcp"}, {"日志 log", 8084, "tcp"},
            {"RADIUS 认证/授权", 1812, "udp"}, {"RADIUS 计费", 1813, "udp"}
    };

    public MonitorService(NetworkService networkService) {
        this.networkService = networkService;
        collect();
        scheduler.scheduleAtFixedRate(this::collectSafe, 5, 5, TimeUnit.SECONDS);
    }

    private void collectSafe() {
        try {
            collect();
        } catch (Exception e) {
            log.warn("监控采集异常: {}", e.getMessage());
        }
    }

    private synchronized void collect() {
        CentralProcessor cpu = si.getHardware().getProcessor();
        long[] ticks = cpu.getSystemCpuLoadTicks();
        double cpuUsage = 0;
        if (prevTicks != null) {
            double d = cpu.getSystemCpuLoadBetweenTicks(prevTicks);
            if (!Double.isNaN(d)) cpuUsage = d * 100;
        }
        prevTicks = ticks;

        GlobalMemory mem = si.getHardware().getMemory();
        long total = mem.getTotal();
        long avail = mem.getAvailable();
        long used = Math.max(0, total - avail);
        long cached = cachedBytes();
        double memUsage = total > 0 ? used * 100.0 / total : 0;

        long now = System.currentTimeMillis();
        double interval = prevTs > 0 ? (now - prevTs) / 1000.0 : 5.0;
        if (interval <= 0) interval = 5.0;
        prevTs = now;

        // 接口清单与“网络管理”同源（NetworkService），吞吐速率由 OSHI 计数器差分得到
        Map<String, NetworkIF> oshiByName = new HashMap<>();
        for (NetworkIF n : si.getHardware().getNetworkIFs()) {
            n.updateAttributes();
            oshiByName.put(n.getName(), n);
        }
        List<MonitorSnapshot.Net> netList = new ArrayList<>();
        for (NetInterface base : networkService.listBasic()) {
            NetworkIF n = oshiByName.get(base.getName());
            long rx = n != null ? n.getBytesRecv() : 0;
            long tx = n != null ? n.getBytesSent() : 0;
            long[] p = prevNet.get(base.getName());
            long rxRate = p != null ? Math.max(0, (long) ((rx - p[0]) / interval)) : 0;
            long txRate = p != null ? Math.max(0, (long) ((tx - p[1]) / interval)) : 0;
            prevNet.put(base.getName(), new long[]{rx, tx});

            MonitorSnapshot.Net ni = new MonitorSnapshot.Net();
            ni.setName(base.getName());
            ni.setDisplayName(base.getOperState());
            ni.setUp(base.isLinkUp());
            ni.setSpeedMbps(base.getSpeedMbps());
            ni.setRxRateBps(rxRate);
            ni.setTxRateBps(txRate);
            netList.add(ni);
        }

        MonitorSnapshot s = new MonitorSnapshot();
        MonitorSnapshot.Cpu c = new MonitorSnapshot.Cpu();
        c.setModel(trim(cpu.getProcessorIdentifier().getName()));
        c.setPhysicalCores(cpu.getPhysicalProcessorCount());
        c.setLogicalCores(cpu.getLogicalProcessorCount());
        c.setUsage(round(cpuUsage));
        s.setCpu(c);

        MonitorSnapshot.Memory m = new MonitorSnapshot.Memory();
        m.setTotalBytes(total);
        m.setUsedBytes(used);
        m.setCachedBytes(cached);
        m.setAvailableBytes(avail);
        m.setUsagePercent(round(memUsage));
        m.setFrequencyMHz(memoryFrequency());
        s.setMemory(m);

        s.setNetwork(netList);
        this.latest = s;

        pushLive();
    }

    private void pushLive() {
        if (emitters.isEmpty()) return;
        String json = liveJson();
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name("metrics").data(json));
            } catch (Exception ex) {
                emitters.remove(e);
                try { e.complete(); } catch (Exception ignore) { }
            }
        }
    }

    private String liveJson() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cpu", latest.getCpu() == null ? 0 : latest.getCpu().getUsage());
        data.put("mem", latest.getMemory() == null ? 0 : latest.getMemory().getUsagePercent());
        data.put("ts", System.currentTimeMillis());
        List<Map<String, Object>> nets = new ArrayList<>();
        if (latest.getNetwork() != null) {
            for (MonitorSnapshot.Net n : latest.getNetwork()) {
                Map<String, Object> nm = new LinkedHashMap<>();
                nm.put("name", n.getName());
                nm.put("rx", n.getRxRateBps());
                nm.put("tx", n.getTxRateBps());
                nets.add(nm);
            }
        }
        data.put("net", nets);
        return JSON.toJSONString(data);
    }

    /** 全量快照（含服务状态） */
    public synchronized MonitorSnapshot snapshot() {
        MonitorSnapshot s = new MonitorSnapshot();
        s.setCpu(latest.getCpu());
        s.setMemory(latest.getMemory());
        s.setNetwork(latest.getNetwork() == null ? Collections.emptyList() : new ArrayList<>(latest.getNetwork()));
        s.setServices(checkServices());
        return s;
    }

    private List<MonitorSnapshot.Svc> checkServices() {
        Set<String> udpPorts = udpListeningPorts();
        List<MonitorSnapshot.Svc> list = new ArrayList<>();
        for (Object[] svc : SERVICES) {
            String name = (String) svc[0];
            int port = (Integer) svc[1];
            String proto = svc.length > 2 ? (String) svc[2] : "tcp";
            MonitorSnapshot.Svc s = new MonitorSnapshot.Svc();
            s.setName(name);
            s.setPort(port);
            s.setOnline("udp".equals(proto) ? udpPorts.contains(String.valueOf(port)) : portOpen(port));
            list.add(s);
        }
        return list;
    }

    /** 读取本机正在监听的 UDP 端口（运行 ss -lun，解析 Local Address:Port 的端口号）。 */
    private Set<String> udpListeningPorts() {
        Set<String> ports = new HashSet<>();
        try {
            Process p = new ProcessBuilder("ss", "-lun").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(2, TimeUnit.SECONDS);
            for (String line : out.split("\\n")) {
                for (String tok : line.trim().split("\\s+")) {
                    int c = tok.lastIndexOf(':');            // 形如 0.0.0.0:1812 / [::]:1813 / *:1812
                    if (c > 0 && c < tok.length() - 1) {
                        String port = tok.substring(c + 1);
                        if (port.matches("\\d{1,5}")) ports.add(port);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取 UDP 监听端口失败: {}", e.getMessage());
        }
        return ports;
    }

    private boolean portOpen(int port) {
        try (Socket sk = new Socket()) {
            sk.connect(new InetSocketAddress("127.0.0.1", port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 缓存（buff/cache）：Cached + Buffers + SReclaimable，读 /proc/meminfo（单位 kB→字节）；非 Linux 返回 0。 */
    private long cachedBytes() {
        try {
            long kb = 0;
            for (String line : Files.readAllLines(Path.of("/proc/meminfo"))) {
                if (line.startsWith("Cached:") || line.startsWith("Buffers:") || line.startsWith("SReclaimable:")) {
                    int colon = line.indexOf(':');
                    int kbIdx = line.indexOf("kB", colon);
                    if (colon > 0 && kbIdx > colon) {
                        String num = line.substring(colon + 1, kbIdx).trim();
                        kb += Long.parseLong(num);
                    }
                }
            }
            return kb * 1024L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 内存频率：尽力读取 dmidecode（需 root，无权限返回 null，前端显示“—”） */
    private Integer memoryFrequency() {
        if (freqTried) return memFreq;
        freqTried = true;
        try {
            Process p = new ProcessBuilder("sh", "-c",
                    "dmidecode -t memory 2>/dev/null | grep -i -m1 'Configured Clock Speed'").start();
            String out = new String(p.getInputStream().readAllBytes());
            Matcher mt = Pattern.compile("(\\d+)").matcher(out);
            if (mt.find()) memFreq = Integer.parseInt(mt.group(1));
            p.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 无权限：忽略
        }
        return memFreq;
    }

    /** SSE 订阅 */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> { emitters.remove(emitter); emitter.complete(); });
        emitter.onError(t -> { emitters.remove(emitter); emitter.complete(); });
        try {
            emitter.send(SseEmitter.event().name("metrics").data(liveJson()));
        } catch (Exception ignore) { }
        return emitter;
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdownNow();
        for (SseEmitter e : emitters) {
            try { e.complete(); } catch (Exception ignore) { }
        }
    }

    private static double round(double v) { return Math.round(v * 10) / 10.0; }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
