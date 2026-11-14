package com.nac.radius.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nac.common.coa.CoaProtocol;
import com.nac.common.coa.CoaQueue;
import com.nac.common.isolation.IsolationProtocol;
import com.nac.common.redis.RedisUtil;
import com.nac.radius.entity.OnlineSession;
import com.nac.radius.mapper.OnlineSessionMapper;
import com.nac.radius.mapper.PortalConfigReader;
import com.nac.radius.packet.RadiusCodec;
import com.nac.radius.packet.RadiusCodes;
import com.nac.radius.packet.RadiusPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RADIUS 动态授权（RFC 5176）服务：CoA-Request 与 Disconnect-Request。
 *
 * <p>适用场景：终端已经在线，但隔离状态发生了变化（心跳检测到软件被卸载、管理端释放/转修复区、
 * 修复完成自动迁回）。认证响应只能影响“下一次认证”，因此需要主动下发：
 * <ul>
 *   <li>{@code DISCONNECT}：断开在线会话，终端重连后由认证链路下发正确 ACL/VLAN（兼容性最好）；</li>
 *   <li>{@code REQUEST}：在线改写授权属性（Filter-Id / Tunnel-*），会话不断开（需 NAS 支持）。</li>
 * </ul>
 *
 * <p>配置读取 sys_portal_config（10s 缓存）：coaEnabled / coaMode / coaPort / coaTimeoutMs /
 * coaRetries / coaMessageAuthenticator / coaNasModes（按 NAS IP 覆盖模式的 JSON）。
 * 默认关闭，需现场确认交换机支持 RFC 5176 后再开启。</p>
 */
@Slf4j
@Service
public class CoaService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long CACHE_TTL_MS = 10_000;

    /** 命令执行结果 */
    public static final String ACK = "ACK";
    public static final String NAK = "NAK";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String NO_SESSION = "NO_SESSION";
    public static final String SKIPPED = "SKIPPED";
    public static final String ERROR = "ERROR";

    private final OnlineSessionMapper sessionMapper;
    private final PortalConfigReader configReader;
    private final RadiusSecretService secretService;
    private final RedisUtil redis;

    private volatile long cfgExpire;
    private volatile boolean enabled;
    private volatile String mode = CoaProtocol.MODE_DISCONNECT;
    private volatile int port = 3799;
    private volatile int timeoutMs = 2000;
    private volatile int retries = 1;
    private volatile boolean withMessageAuthenticator;
    private volatile Map<String, String> nasModes = Map.of();

    public CoaService(OnlineSessionMapper sessionMapper, PortalConfigReader configReader,
                      RadiusSecretService secretService, RedisUtil redis) {
        this.sessionMapper = sessionMapper;
        this.configReader = configReader;
        this.secretService = secretService;
        this.redis = redis;
    }

    // ---------------- 配置 ----------------

    private void refreshConfig() {
        long now = System.currentTimeMillis();
        if (now < cfgExpire) return;
        boolean en = false;
        String md = CoaProtocol.MODE_DISCONNECT;
        int pt = 3799, tm = 2000, rt = 1;
        boolean ma = false;
        Map<String, String> nm = Map.of();
        try {
            String v = cfg("coaEnabled");
            en = v != null && "true".equalsIgnoreCase(v.trim());
            String m = cfg("coaMode");
            if (m != null && !m.isBlank()) md = m.trim().toUpperCase(Locale.ROOT);
            pt = intCfg("coaPort", pt, 1, 65535);
            tm = intCfg("coaTimeoutMs", tm, 200, 10_000);
            rt = intCfg("coaRetries", rt, 0, 3);
            String maV = cfg("coaMessageAuthenticator");
            ma = maV != null && "true".equalsIgnoreCase(maV.trim());
            String nmV = cfg("coaNasModes");
            if (nmV != null && !nmV.isBlank()) {
                Map<String, String> parsed = MAPPER.readValue(nmV, new TypeReference<Map<String, String>>() {});
                Map<String, String> norm = new LinkedHashMap<>();
                parsed.forEach((k, val) -> {
                    if (k != null && val != null) norm.put(k.trim(), val.trim().toUpperCase(Locale.ROOT));
                });
                nm = Map.copyOf(norm);
            }
        } catch (Exception e) {
            log.warn("CoA 配置读取失败，沿用上次值: {}", e.getMessage());
        }
        enabled = en;
        mode = md;
        port = pt;
        timeoutMs = tm;
        retries = rt;
        withMessageAuthenticator = ma;
        nasModes = nm;
        cfgExpire = now + CACHE_TTL_MS;
    }

    private String cfg(String key) {
        try {
            return configReader.selectValue(key);
        } catch (Exception e) {
            return null;
        }
    }

    private int intCfg(String key, int def, int min, int max) {
        String v = cfg(key);
        if (v == null || v.isBlank()) return def;
        try {
            int n = Integer.parseInt(v.trim());
            return Math.min(Math.max(n, min), max);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 管理端展示的当前生效配置与队列深度。 */
    public Map<String, Object> view() {
        refreshConfig();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("mode", mode);
        m.put("port", port);
        m.put("timeoutMs", timeoutMs);
        m.put("retries", retries);
        m.put("messageAuthenticator", withMessageAuthenticator);
        m.put("nasModes", nasModes);
        m.put("queueDepth", redis.llen(CoaProtocol.QUEUE_KEY));
        return m;
    }

    // ---------------- 投递 ----------------

    /**
     * 投递一条动态授权命令（异步执行，绝不阻塞认证/心跳线程）。
     * 关闭开关或同一终端同一动作在去重窗口内重复时直接丢弃。
     */
    public void enqueue(String mac, String action, String zone, String channelType,
                        String channelValue, String reason) {
        refreshConfig();
        if (!enabled) return;
        String m = IsolationProtocol.normalizeMac(mac);
        if (m.length() != 12) return;
        // DISCONNECT 模式下 AUTH_CHANGE 实际也走断线，去重键统一，避免同一事件发两个报文
        String dedupeAction = CoaProtocol.MODE_DISCONNECT.equalsIgnoreCase(mode)
                && CoaProtocol.ACTION_AUTH_CHANGE.equalsIgnoreCase(action)
                ? CoaProtocol.ACTION_DISCONNECT : action;
        if (!redis.setIfAbsent(CoaProtocol.DEDUP_PREFIX + m + ":" + dedupeAction, CoaProtocol.DEDUP_TTL)) return;
        CoaQueue.enqueue(redis, new CoaProtocol.Command(m, action, zone, channelType,
                channelValue, truncate(reason, 100), System.currentTimeMillis()));
    }

    /** 管理端手动触发（忽略总开关，仍遵守 NAS 级模式；用于现场能力验证）。 */
    public Map<String, Object> sendNow(String mac, String action, String zone,
                                       String channelType, String channelValue) {
        refreshConfig();
        String m = IsolationProtocol.normalizeMac(mac);
        String act = action == null || action.isBlank() ? CoaProtocol.ACTION_DISCONNECT
                : action.trim().toUpperCase(Locale.ROOT);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mac", m);
        out.put("action", act);
        if (m.length() != 12) {
            out.put("result", ERROR);
            out.put("message", "MAC 格式不合法");
            return out;
        }
        String r = execute(new CoaProtocol.Command(m, act, zone, channelType, channelValue,
                "管理员手动下发", System.currentTimeMillis()), true);
        out.put("result", r);
        out.put("message", ACK.equals(r) ? "NAS 已确认" : "未确认（" + r + "）");
        return out;
    }

    // ---------------- 执行 ----------------

    /**
     * 执行命令。
     *
     * @param force true=忽略总开关（管理端手动测试）；false=按配置
     * @return ACK/NAK/TIMEOUT/NO_SESSION/SKIPPED/ERROR
     */
    public String execute(CoaProtocol.Command c, boolean force) {
        refreshConfig();
        if (!force && !enabled) return SKIPPED;
        if (c == null) return ERROR;
        String m = IsolationProtocol.normalizeMac(c.mac());
        if (m.length() != 12) return ERROR;

        OnlineSession session = null;
        try {
            session = sessionMapper.selectLatestByMac(m);
        } catch (Exception e) {
            log.warn("CoA 查询在线会话失败: mac={} {}", m, e.getMessage());
            return ERROR;
        }
        // 仅对在线会话下发（selectLatestByMac 可能返回已下线历史会话）
        if (session == null || session.getStatus() == null || session.getStatus() != 1
                || session.getNasIp() == null || session.getNasIp().isBlank()) {
            log.debug("CoA 跳过：无在线会话 mac={}", m);
            return NO_SESSION;
        }

        String nasIp = session.getNasIp().trim();
        String effMode = nasModes.getOrDefault(nasIp, mode);
        if (force && CoaProtocol.MODE_NONE.equals(effMode)) effMode = CoaProtocol.MODE_DISCONNECT;
        if (CoaProtocol.MODE_NONE.equals(effMode)) return SKIPPED;

        boolean disconnect = CoaProtocol.ACTION_DISCONNECT.equalsIgnoreCase(c.action())
                || CoaProtocol.MODE_DISCONNECT.equals(effMode);
        byte code = disconnect ? RadiusCodes.DISCONNECT_REQUEST : RadiusCodes.COA_REQUEST;
        byte id = (byte) ThreadLocalRandom.current().nextInt(256);
        RadiusPacket pkt = new RadiusPacket(code, id, null);
        if (!build(pkt, session, disconnect, c)) {
            log.debug("CoA 跳过：会话缺少可定位标识 mac={} session={}", m, session.getAcctSessionId());
            return NO_SESSION;
        }

        String secret = secretService.getSharedSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("CoA 跳过：未配置 RADIUS 共享密钥");
            return SKIPPED;
        }
        String result = send(nasIp, pkt, code, secret, disconnect ? "disconnect" : "auth-change", m);
        log.info("metric=coa_send action={} mac={} nas={} result={}",
                disconnect ? "DISCONNECT" : "AUTH_CHANGE", m, nasIp, result);
        return result;
    }

    /** 组装会话定位属性与（授权变更时的）下发通道属性；缺少任何定位属性返回 false。 */
    private boolean build(RadiusPacket pkt, OnlineSession s, boolean disconnect, CoaProtocol.Command c) {
        boolean hasId = false;
        if (notBlank(s.getAcctSessionId())) {
            pkt.addString(RadiusCodes.ACCT_SESSION_ID, s.getAcctSessionId());
            hasId = true;
        }
        if (notBlank(s.getUsername())) {
            pkt.addString(RadiusCodes.USER_NAME, s.getUsername());
            hasId = true;
        }
        pkt.addString(RadiusCodes.CALLING_STATION_ID, formatMac(s.getMac()));
        Integer ip = ipv4(s.getFramedIp());
        if (ip != null) pkt.addInt(RadiusCodes.FRAMED_IP_ADDRESS, ip);
        if (!hasId) return false;

        if (!disconnect) {
            String type = c.channelType() == null ? "" : c.channelType().trim();
            String value = c.channelValue() == null ? "" : c.channelValue().trim();
            if (IsolationProtocol.MODE_VLAN.equalsIgnoreCase(type)) {
                int vlan = safeInt(value, 0);
                if (vlan > 0 && vlan <= 4094) {
                    pkt.addInt(RadiusCodes.TUNNEL_TYPE, 13);
                    pkt.addInt(RadiusCodes.TUNNEL_MEDIUM_TYPE, 6);
                    pkt.addString(RadiusCodes.TUNNEL_PRIVATE_GROUP_ID, String.valueOf(vlan));
                }
            } else if (!value.isEmpty()) {
                pkt.addString(RadiusCodes.FILTER_ID, value);
            }
        }
        String reason = c.reason() == null ? "" : c.reason();
        if (!reason.isBlank()) pkt.addString(RadiusCodes.REPLY_MESSAGE, truncate(reason, 200));
        return true;
    }

    /** 发送并按超时重传，返回 ACK/NAK/TIMEOUT。 */
    private String send(String nasIp, RadiusPacket pkt, byte code, String secret, String kind, String mac) {
        byte expectedAck = code == RadiusCodes.DISCONNECT_REQUEST ? RadiusCodes.DISCONNECT_ACK : RadiusCodes.COA_ACK;
        byte[] req;
        try {
            req = RadiusCodec.encodeRequest(pkt.getCode(), pkt.getIdentifier(), pkt, secret, withMessageAuthenticator);
        } catch (Exception e) {
            log.warn("CoA 编码失败: mac={} {}", mac, e.getMessage());
            return ERROR;
        }
        byte[] requestAuth = java.util.Arrays.copyOfRange(req, 4, 20);
        InetAddress addr;
        try {
            addr = InetAddress.getByName(nasIp);
        } catch (Exception e) {
            log.warn("CoA 解析 NAS 地址失败: nas={} {}", nasIp, e.getMessage());
            return ERROR;
        }
        long start = System.currentTimeMillis();
        for (int attempt = 1; attempt <= retries + 1; attempt++) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(timeoutMs);
                socket.send(new DatagramPacket(req, req.length, addr, port));
                byte[] buf = new byte[4096];
                DatagramPacket resp = new DatagramPacket(buf, buf.length);
                socket.receive(resp);
                byte[] data = java.util.Arrays.copyOf(resp.getData(), resp.getLength());
                RadiusPacket r = RadiusCodec.parse(data, data.length);
                if (r == null || !RadiusCodec.verifyResponseAuthenticator(data, requestAuth, secret)) {
                    log.warn("CoA 应答非法（认证符校验失败）: mac={} nas={} attempt={}", mac, nasIp, attempt);
                    continue;
                }
                if (r.getCode() == expectedAck) return ACK;
                if (r.getCode() == RadiusCodes.COA_NAK || r.getCode() == RadiusCodes.DISCONNECT_NAK) {
                    String msg = r.getString(RadiusCodes.REPLY_MESSAGE);
                    log.info("CoA 被 NAS 拒绝: mac={} nas={} {} msg={}", mac, nasIp, kind, msg);
                    return NAK;
                }
                log.warn("CoA 应答 code={} 非预期: mac={} nas={}", r.getCode(), mac, nasIp);
            } catch (java.net.SocketTimeoutException te) {
                log.debug("CoA 超时: mac={} nas={} attempt={}", mac, nasIp, attempt);
            } catch (Exception e) {
                log.warn("CoA 发送失败: mac={} nas={} {}", mac, nasIp, e.getMessage());
                return ERROR;
            }
        }
        log.info("CoA 无应答（已重试 {} 次）: mac={} nas={} costMs={}",
                retries, mac, nasIp, System.currentTimeMillis() - start);
        return TIMEOUT;
    }

    // ---------------- 工具 ----------------

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 12 位小写 MAC → AA-BB-CC-DD-EE-FF（Calling-Station-Id 常见格式）。 */
    private static String formatMac(String mac) {
        String m = IsolationProtocol.normalizeMac(mac);
        if (m.length() != 12) return "";
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) sb.append('-');
            sb.append(m.substring(i, i + 2).toUpperCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static Integer ipv4(String ip) {
        if (ip == null) return null;
        String[] p = ip.trim().split("\\.");
        if (p.length != 4) return null;
        int v = 0;
        for (String s : p) {
            try {
                int n = Integer.parseInt(s);
                if (n < 0 || n > 255) return null;
                v = (v << 8) | n;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return v;
    }

    private static int safeInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
