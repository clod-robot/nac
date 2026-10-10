package com.nac.radius.service;

import com.nac.common.security.SensitiveUtil;
import com.nac.common.syslog.SyslogForwarder;
import com.nac.radius.entity.AuthLog;
import com.nac.radius.entity.OnlineSession;
import com.nac.radius.mapper.AuthLogMapper;
import com.nac.radius.mapper.OnlineSessionMapper;
import com.nac.radius.mapper.PortalConfigReader;
import com.nac.radius.packet.PortalPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CMCC Portal v2.0 协议认证服务。
 *
 * 流程（CHAP，默认）：
 *   1. NAS →  REQ_CHALLENGE(带 UserName)
 *   2. 本端 → ACK_CHALLENGE(回 16 字节随机 Challenge)
 *   3. NAS →  REQ_AUTH(ChapPassword = MD5(ReqId + 共享密钥 + Challenge))
 *   4. 本端校验共享密钥与 ChapPassword，通过则落在线会话 + 认证日志，回 ACK_AUTH 成功
 *   5. NAS →  AFF_ACK_AUTH 确认；或 REQ_LOGOUT / NTF_LOGOUT 下线
 *
 * 安全：开启但未配置共享密钥时拒绝认证（防误开放）；挑战一次性、短 TTL；常量时间比较防时序攻击。
 */
@Slf4j
@Service
public class PortalV2Service {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CHALLENGE_TTL_MS = 60_000;

    private final PortalConfigReader configReader;
    private final OnlineSessionMapper sessionMapper;
    private final AuthLogMapper authLogMapper;
    private final SyslogForwarder syslogForwarder;

    /** serialNo -> {challenge, reqId, ts} */
    private final ConcurrentHashMap<Integer, Challenge> challenges = new ConcurrentHashMap<>();

    private record Challenge(byte[] value, byte reqId, long ts) {}

    public PortalV2Service(PortalConfigReader configReader, OnlineSessionMapper sessionMapper,
                           AuthLogMapper authLogMapper, SyslogForwarder syslogForwarder) {
        this.configReader = configReader;
        this.sessionMapper = sessionMapper;
        this.authLogMapper = authLogMapper;
        this.syslogForwarder = syslogForwarder;
    }

    public boolean isEnabled() {
        return "true".equalsIgnoreCase(cfg("portalV2Enabled"));
    }

    /** 允许的协议版本：v1 / v2 / both（默认 both 兼容）。 */
    public boolean versionAllowed(byte version) {
        String p = cfg("portalProtocol");
        if (p == null || p.isBlank() || "both".equalsIgnoreCase(p)) return true;
        if ("v1".equalsIgnoreCase(p) || "1".equals(p)) return version == PortalPacket.VERSION_1;
        if ("v2".equalsIgnoreCase(p) || "2".equals(p)) return version == PortalPacket.VERSION_2;
        return true;
    }

    /** 原始协议版本配置值（both/v1/v2），供状态展示。 */
    public String protocolConfig() {
        String p = cfg("portalProtocol");
        return (p == null || p.isBlank()) ? "both" : p;
    }

    public int getPort() {
        try { return Integer.parseInt(cfg("portalV2Port")); } catch (Exception e) { return 2000; }
    }

    private String cfg(String key) {
        try { return configReader.selectValue(key); } catch (Exception e) { return null; }
    }

    private String sharedSecret() {
        String s = cfg("portalV2Secret");
        return s == null ? "" : s;
    }

    /** 处理一个入站报文，返回响应报文；无需响应返回 null。 */
    public PortalPacket handle(PortalPacket req, InetSocketAddress sender) {
        if (req == null) return null;
        return switch (req.getType()) {
            case PortalPacket.TYPE_REQ_CHALLENGE -> onChallenge(req);
            case PortalPacket.TYPE_REQ_AUTH -> onAuth(req, sender);
            case PortalPacket.TYPE_REQ_LOGOUT, PortalPacket.TYPE_NTF_LOGOUT -> onLogout(req, sender);
            case PortalPacket.TYPE_AFF_ACK_AUTH -> { log.debug("Portal AFF_ACK_AUTH serial={}", req.getSerialNo()); yield null; }
            default -> null;
        };
    }

    private PortalPacket onChallenge(PortalPacket req) {
        byte[] challenge = new byte[16];
        RANDOM.nextBytes(challenge);
        challenges.put(req.getSerialNo(), new Challenge(challenge, req.getReqId(), System.currentTimeMillis()));

        PortalPacket ack = baseAck(req, PortalPacket.TYPE_ACK_CHALLENGE);
        ack.setAuthType(PortalPacket.AUTH_CHAP);
        ack.putAttr(PortalPacket.ATTR_CHALLENGE, challenge);
        // CHAP 报文需带 ChapAuth：MD5(ReqId + 密钥 + Challenge)
        ack.setChapAuth(chapAuth(ack.getReqId(), sharedSecret(), challenge));
        return ack;
    }

    private PortalPacket onAuth(PortalPacket req, InetSocketAddress sender) {
        String user = req.getAttrString(PortalPacket.ATTR_USER_NAME);
        String ip = req.userIpString();
        String nasIp = sender.getAddress().getHostAddress();
        String secret = sharedSecret();

        PortalPacket ack = baseAck(req, PortalPacket.TYPE_ACK_AUTH);
        ack.setAuthType(req.getAuthType());

        boolean ok = false;
        String msg;
        if (secret.isBlank()) {
            msg = "Portal 未配置共享密钥，拒绝";
        } else if (req.getAuthType() == PortalPacket.AUTH_CHAP) {
            // CHAP（v2）：必须先经挑战握手
            Challenge c = challenges.remove(req.getSerialNo());
            if (c == null || System.currentTimeMillis() - c.ts() > CHALLENGE_TTL_MS) {
                msg = "挑战缺失或已过期";
            } else {
                byte[] expected = chapAuth(req.getReqId(), secret, c.value());
                byte[] got = req.getChapAuth();
                ok = got != null && constantTimeEquals(expected, got);
                msg = ok ? "认证成功" : "CHAP 校验失败";
            }
        } else {
            // PAP（v1 常用）：密码属性直接比对共享密钥，无需挑战
            byte[] pw = req.getAttrs().get(PortalPacket.ATTR_PASSWORD);
            ok = pw != null && constantTimeEquals(secret.getBytes(StandardCharsets.UTF_8), pw);
            msg = ok ? "认证成功" : "PAP 口令错误";
        }

        ack.setErrCode((byte) (ok ? 0 : 1));
        if (ok) {
            recordOnline(user, ip, nasIp, req.getSerialNo());
            log.info("Portal v{} 认证成功: user={} ip={} nas={}", req.getVersion(), mask(user), ip, nasIp);
        } else {
            log.info("Portal v{} 认证拒绝: user={} ip={} nas={} msg={}", req.getVersion(), mask(user), ip, nasIp, msg);
        }
        recordAuthLog(user, ip, nasIp, ok ? 1 : 0, msg);
        // CHAP 成功包带 ChapAuth 回签（PAP/v1 不带 trailer）
        if (ok && ack.getAuthType() == PortalPacket.AUTH_CHAP) {
            ack.setChapAuth(chapAuth(ack.getReqId(), secret, req.getChapAuth() == null ? new byte[16] : req.getChapAuth()));
        }
        return ack;
    }

    private PortalPacket onLogout(PortalPacket req, InetSocketAddress sender) {
        String ip = req.userIpString();
        try { sessionMapper.markOffline("portal:" + req.getSerialNo()); } catch (Exception ignored) {}
        // 也按 IP 清理（尽力）
        try {
            OnlineSession s = sessionMapper.selectBySessionId("portal:ip:" + ip);
            if (s != null) sessionMapper.markOffline(s.getAcctSessionId());
        } catch (Exception ignored) {}
        log.info("Portal v2 下线: ip={} nas={}", ip, sender.getAddress().getHostAddress());
        PortalPacket ack = baseAck(req, PortalPacket.TYPE_ACK_LOGOUT);
        return ack;
    }

    private PortalPacket baseAck(PortalPacket req, byte type) {
        PortalPacket p = new PortalPacket();
        p.setVersion(req.getVersion());   // 回包沿用请求版本(v1/v2)
        p.setType(type);
        p.setAuthType(req.getAuthType());
        p.setSerialNo(req.getSerialNo());
        p.setReqId((byte) (req.getReqId() + 1));
        p.setUserIpRaw(req.getUserIpRaw());
        p.setUserPort(req.getUserPort());
        return p;
    }

    private void recordOnline(String user, String ip, String nasIp, int serial) {
        try {
            OnlineSession s = new OnlineSession();
            s.setAcctSessionId("portal:" + serial);
            s.setUsername(user == null ? "portal" : user);
            s.setUsernameMask(user == null ? "portal" : SensitiveUtil.mask(user, 1, 1));
            s.setFramedIp(ip);
            s.setNasIp(nasIp);
            s.setStatus(1);
            s.setStartTime(LocalDateTime.now());
            s.setUpdateTime(LocalDateTime.now());
            sessionMapper.upsert(s);
        } catch (Exception e) {
            log.warn("Portal v2 在线会话写入失败: {}", e.getMessage());
        }
    }

    private void recordAuthLog(String user, String ip, String nasIp, int result, String msg) {
        try {
            AuthLog l = new AuthLog();
            l.setAuthType("portal-v2");
            l.setUsername(user);
            l.setIp(ip);
            l.setNasIp(nasIp);
            l.setResult(result);
            l.setMessage(msg);
            l.setCreateTime(LocalDateTime.now());
            authLogMapper.insert(l);
            syslogForwarder.forwardAuthLog("authLog type=portal-v2 user=" + nz(user) + " ip=" + nz(ip)
                    + " nas=" + nz(nasIp) + " result=" + result + " msg=" + nz(msg));
        } catch (Exception e) {
            log.warn("Portal v2 认证日志写入失败: {}", e.getMessage());
        }
    }

    private static byte[] chapAuth(byte reqId, String secret, byte[] challenge) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(reqId);
            md.update(secret.getBytes(StandardCharsets.UTF_8));
            md.update(challenge);
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int r = 0;
        for (int i = 0; i < a.length; i++) r |= a[i] ^ b[i];
        return r == 0;
    }

    private static String mask(String s) { return s == null ? "-" : SensitiveUtil.mask(s, 1, 1); }
    private static String nz(String s) { return s == null ? "-" : s; }
}
