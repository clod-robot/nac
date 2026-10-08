package com.nac.radius.service;

import com.nac.common.redis.RedisUtil;
import com.nac.common.security.AesCryptoUtil;
import com.nac.common.syslog.SyslogForwarder;
import com.nac.radius.config.RadiusProperties;
import com.nac.radius.entity.AuthLog;
import com.nac.radius.entity.RadiusUser;
import com.nac.radius.mapper.AuthLogMapper;
import com.nac.radius.mapper.ExemptTerminalMapper;
import com.nac.radius.mapper.NasMapper;
import com.nac.radius.mapper.RadiusUserMapper;
import com.nac.radius.packet.RadiusCodes;
import com.nac.radius.packet.RadiusCodec;
import com.nac.radius.packet.RadiusPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * RADIUS 认证（RFC 2865）：支持 PAP 与 CHAP。
 * 安全策略：用户禁用/锁定拒绝；失败计数 Redis 防爆破；明文口令不落日志。
 */
@Slf4j
@Service
public class RadiusAuthService {

    private final RadiusUserMapper userMapper;
    private final AuthLogMapper authLogMapper;
    private final NasMapper nasMapper;
    private final RedisUtil redisUtil;
    private final AesCryptoUtil aesCryptoUtil;
    private final RadiusProperties props;
    private final RadiusSecretService secretService;
    private final ExemptTerminalMapper exemptMapper;
    private final SyslogForwarder syslogForwarder;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public RadiusAuthService(RadiusUserMapper userMapper, AuthLogMapper authLogMapper, NasMapper nasMapper,
                             RedisUtil redisUtil, AesCryptoUtil aesCryptoUtil, RadiusProperties props,
                             RadiusSecretService secretService, ExemptTerminalMapper exemptMapper,
                             SyslogForwarder syslogForwarder) {
        this.userMapper = userMapper;
        this.authLogMapper = authLogMapper;
        this.nasMapper = nasMapper;
        this.redisUtil = redisUtil;
        this.aesCryptoUtil = aesCryptoUtil;
        this.props = props;
        this.secretService = secretService;
        this.exemptMapper = exemptMapper;
        this.syslogForwarder = syslogForwarder;
    }

    /** 处理 Access-Request，返回响应报文（Accept/Reject），异常时返回 Reject。 */
    public RadiusPacket authenticate(RadiusPacket request) {
        String username = request.getString(RadiusCodes.USER_NAME);
        RadiusPacket reject = new RadiusPacket(RadiusCodes.ACCESS_REJECT, request.getIdentifier(), null);

        // 免认证终端：Calling-Station-Id(MAC) 或 Framed-IP 命中白名单直接 Accept（MAB 放行）
        String callingMac = request.getString(RadiusCodes.CALLING_STATION_ID);
        String framedIp = framedIpOf(request);
        if (isExempt(callingMac, framedIp)) {
            RadiusPacket accept = new RadiusPacket(RadiusCodes.ACCESS_ACCEPT, request.getIdentifier(), null);
            accept.addInt(RadiusCodes.SESSION_TIMEOUT, props.getSessionTimeout());
            if (props.getVlanId() > 0) {
                accept.addInt(RadiusCodes.TUNNEL_TYPE, 13);
                accept.addInt(RadiusCodes.TUNNEL_MEDIUM_TYPE, 6);
                accept.addString(RadiusCodes.TUNNEL_PRIVATE_GROUP_ID, String.valueOf(props.getVlanId()));
            }
            accept.addString(RadiusCodes.REPLY_MESSAGE, "exempt terminal accepted");
            recordAuth(request, 1, "免认证终端放行");
            log.info("RADIUS 免认证放行: mac={} ip={}", callingMac, framedIp);
            return accept;
        }

        if (isBlank(username)) {
            reject.addString(RadiusCodes.REPLY_MESSAGE, "missing username");
            recordAuth(request, 0, "missing username");
            return reject;
        }
        // 防爆破：该用户是否处于失败锁定窗口
        if (isUserLocked(username)) {
            log.info("RADIUS 认证拒绝(锁定中): user={}", mask(username));
            reject.addString(RadiusCodes.REPLY_MESSAGE, "account temporarily locked");
            recordAuth(request, 0, "账号锁定中");
            return reject;
        }

        RadiusUser user;
        try {
            user = userMapper.selectByUsername(username);
        } catch (Exception e) {
            log.error("RADIUS 查询用户失败: {}", e.getMessage());
            reject.addString(RadiusCodes.REPLY_MESSAGE, "server error");
            recordAuth(request, 0, "服务异常");
            return reject;
        }
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            registerFailure(username);
            reject.addString(RadiusCodes.REPLY_MESSAGE, "invalid credentials");
            recordAuth(request, 0, "账号不存在或已禁用");
            return reject;
        }

        // 802.1X：请求携带 EAP-Message 时走 EAP 状态机（当前实现 EAP-MD5，RFC 3748/2869）
        if (request.getAttr(RadiusCodes.EAP_MESSAGE) != null) {
            return handleEap(request, user);
        }

        boolean ok = verify(request, user);
        if (!ok) {
            registerFailure(username);
            log.info("RADIUS 认证失败: user={}", mask(username));
            reject.addString(RadiusCodes.REPLY_MESSAGE, "invalid credentials");
            recordAuth(request, 0, "口令校验失败");
            return reject;
        }

        // 成功：清失败计数，构造 Accept + 授权属性
        clearFailures(username);
        RadiusPacket accept = new RadiusPacket(RadiusCodes.ACCESS_ACCEPT, request.getIdentifier(), null);
        accept.addInt(RadiusCodes.SESSION_TIMEOUT, props.getSessionTimeout());
        if (props.getVlanId() > 0) {
            // Tunnel-Type = VLAN(13), Tunnel-Medium-Type = 802(6), Tunnel-Private-Group-Id = vlan
            accept.addInt(RadiusCodes.TUNNEL_TYPE, 13);
            accept.addInt(RadiusCodes.TUNNEL_MEDIUM_TYPE, 6);
            accept.addString(RadiusCodes.TUNNEL_PRIVATE_GROUP_ID, String.valueOf(props.getVlanId()));
        }
        accept.addString(RadiusCodes.REPLY_MESSAGE, "Welcome " + mask(username));
        recordAuth(request, 1, "认证成功");
        log.info("RADIUS 认证成功: user={} nas={}", mask(username), nasIpOf(request));
        return accept;
    }

    /**
     * 802.1X EAP 状态机：仅实现 EAP-MD5。
     * Identity -> 下发 MD5 挑战(Access-Challenge + State)；MD5-Challenge 响应 -> 校验后 Accept/Reject。
     * 挑战随 State 属性无状态回传（NAS 原样带回），无需服务端会话存储。
     */
    private RadiusPacket handleEap(RadiusPacket request, RadiusUser user) {
        byte[] eap = concatEap(request);
        if (eap == null || eap.length < 4) return eapReject(request, user, "malformed eap", 0);
        int code = eap[0] & 0xFF;
        int id = eap[1] & 0xFF;
        int len = ((eap[2] & 0xFF) << 8) | (eap[3] & 0xFF);
        if (len < 4 || len > eap.length) return eapReject(request, user, "bad eap length", id);
        int type = (code == RadiusCodes.EAP_REQUEST || code == RadiusCodes.EAP_RESPONSE) && eap.length >= 5 ? (eap[4] & 0xFF) : -1;

        // Response/Identity -> 下发 EAP-Request/MD5-Challenge
        if (code == RadiusCodes.EAP_RESPONSE && type == RadiusCodes.EAP_TYPE_IDENTITY) {
            byte[] challenge = new byte[16];
            SECURE_RANDOM.nextBytes(challenge);
            int chalId = (id + 1) & 0xFF;
            byte[] eapReq = new byte[4 + 1 + 1 + 16]; // EAP header + type + valueSize + challenge
            eapReq[0] = (byte) RadiusCodes.EAP_REQUEST;
            eapReq[1] = (byte) chalId;
            eapReq[2] = (byte) (eapReq.length >>> 8);
            eapReq[3] = (byte) eapReq.length;
            eapReq[4] = (byte) RadiusCodes.EAP_TYPE_MD5;
            eapReq[5] = 16;
            System.arraycopy(challenge, 0, eapReq, 6, 16);
            RadiusPacket ch = new RadiusPacket(RadiusCodes.ACCESS_CHALLENGE, request.getIdentifier(), null);
            ch.addAttribute(RadiusCodes.EAP_MESSAGE, eapReq);
            ch.addAttribute(RadiusCodes.STATE, challenge);
            ch.addAttribute(RadiusCodes.MESSAGE_AUTHENTICATOR, new byte[16]);
            return ch;
        }

        // Response/MD5-Challenge -> 校验 response == MD5(eapId + password + challenge)
        if (code == RadiusCodes.EAP_RESPONSE && type == RadiusCodes.EAP_TYPE_MD5) {
            byte[] state = request.getAttr(RadiusCodes.STATE);
            if (state == null || state.length < 16) return eapReject(request, user, "missing state", id);
            byte[] challenge = Arrays.copyOf(state, 16);
            int valueSize = eap[5] & 0xFF;
            if (valueSize != 16 || eap.length < 22) return eapReject(request, user, "bad md5 response", id);
            byte[] resp = Arrays.copyOfRange(eap, 6, 22);
            String plain = resolvePlainPassword(user);
            if (plain == null) return eapReject(request, user, "no reversible password", id);
            byte[] pw = plain.getBytes(StandardCharsets.UTF_8);
            byte[] buf = new byte[1 + pw.length + 16];
            buf[0] = (byte) id;
            System.arraycopy(pw, 0, buf, 1, pw.length);
            System.arraycopy(challenge, 0, buf, 1 + pw.length, 16);
            if (!MessageDigest.isEqual(RadiusCodec.md5(buf), resp)) {
                registerFailure(user.getUsername());
                log.info("RADIUS EAP-MD5 认证失败: user={}", mask(user.getUsername()));
                return eapReject(request, user, "invalid credentials", id);
            }
            clearFailures(user.getUsername());
            RadiusPacket accept = new RadiusPacket(RadiusCodes.ACCESS_ACCEPT, request.getIdentifier(), null);
            accept.addAttribute(RadiusCodes.EAP_MESSAGE, eapPacket(RadiusCodes.EAP_SUCCESS, id, null));
            accept.addInt(RadiusCodes.SESSION_TIMEOUT, props.getSessionTimeout());
            if (props.getVlanId() > 0) {
                accept.addInt(RadiusCodes.TUNNEL_TYPE, 13);
                accept.addInt(RadiusCodes.TUNNEL_MEDIUM_TYPE, 6);
                accept.addString(RadiusCodes.TUNNEL_PRIVATE_GROUP_ID, String.valueOf(props.getVlanId()));
            }
            accept.addString(RadiusCodes.REPLY_MESSAGE, "Welcome " + mask(user.getUsername()));
            accept.addAttribute(RadiusCodes.MESSAGE_AUTHENTICATOR, new byte[16]);
            recordAuth(request, 1, "EAP-MD5 认证成功");
            log.info("RADIUS EAP-MD5 认证成功: user={} nas={}", mask(user.getUsername()), nasIpOf(request));
            return accept;
        }

        return eapReject(request, user, "unsupported eap method", id);
    }

    /** 拼接请求中所有 EAP-Message 属性（跨属性分片）为一个 EAP 报文。 */
    private byte[] concatEap(RadiusPacket request) {
        List<byte[]> parts = request.getAttributes().get(RadiusCodes.EAP_MESSAGE);
        if (parts == null || parts.isEmpty()) return null;
        if (parts.size() == 1) return parts.get(0);
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int off = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, off, p.length); off += p.length; }
        return out;
    }

    /** 构造 EAP 报文：code/id/length/[type+data]。data 为 null 表示 Success/Failure（无 type 字段）。 */
    private byte[] eapPacket(int code, int id, byte[] data) {
        int len = 4 + (data == null ? 0 : data.length);
        byte[] out = new byte[len];
        out[0] = (byte) code;
        out[1] = (byte) id;
        out[2] = (byte) (len >>> 8);
        out[3] = (byte) len;
        if (data != null) System.arraycopy(data, 0, out, 4, data.length);
        return out;
    }

    /** 构造带 EAP-Failure 与 Message-Authenticator 的拒绝响应。 */
    private RadiusPacket eapReject(RadiusPacket request, RadiusUser user, String msg, int eapId) {
        RadiusPacket reject = new RadiusPacket(RadiusCodes.ACCESS_REJECT, request.getIdentifier(), null);
        reject.addAttribute(RadiusCodes.EAP_MESSAGE, eapPacket(RadiusCodes.EAP_FAILURE, eapId, null));
        reject.addString(RadiusCodes.REPLY_MESSAGE, msg);
        reject.addAttribute(RadiusCodes.MESSAGE_AUTHENTICATOR, new byte[16]);
        recordAuth(request, 0, "EAP: " + msg);
        return reject;
    }

    /** 写 RADIUS 认证日志，任何异常不影响认证响应；同时登记/更新来源 NAS 台账 */
    private void recordAuth(RadiusPacket request, int result, String msg) {
        try {
            AuthLog l = new AuthLog();
            l.setAuthType("radius");
            String userMask = mask(request.getString(RadiusCodes.USER_NAME));
            String nasIp = nasIpOf(request);
            l.setUsername(userMask);
            l.setNasIp(nasIp);
            l.setResult(result);
            l.setMessage(msg);
            authLogMapper.insert(l);
            syslogForwarder.forwardAuthLog("authLog type=radius user=" + nz(userMask)
                    + " nasIp=" + nz(nasIp) + " result=" + result + " msg=" + nz(msg));
            // 登记 NAS（无 IP 则跳过）
            if (nasIp != null && !nasIp.isBlank()) {
                nasMapper.touch(nasIp, request.getString(RadiusCodes.NAS_IDENTIFIER), userMask,
                        result == 1 ? 1 : 0, result == 1 ? 0 : 1);
            }
        } catch (Exception e) {
            log.warn("写 RADIUS 认证日志失败: {}", e.getMessage());
        }
    }

    /** NAS-IP-Address 为 4 字节属性，转点分十进制；解析失败回退原字符串。 */
    private String nasIpOf(RadiusPacket request) {
        byte[] ip = request.getAttr(RadiusCodes.NAS_IP_ADDRESS);
        if (ip != null && ip.length == 4) {
            return (ip[0] & 0xff) + "." + (ip[1] & 0xff) + "." + (ip[2] & 0xff) + "." + (ip[3] & 0xff);
        }
        return request.getString(RadiusCodes.NAS_IP_ADDRESS);
    }

    /** Framed-IP-Address 为 4 字节属性，转点分十进制。 */
    private String framedIpOf(RadiusPacket request) {
        byte[] ip = request.getAttr(RadiusCodes.FRAMED_IP_ADDRESS);
        if (ip != null && ip.length == 4) {
            return (ip[0] & 0xff) + "." + (ip[1] & 0xff) + "." + (ip[2] & 0xff) + "." + (ip[3] & 0xff);
        }
        return null;
    }

    /** 命中已启用的 MAC 或 IP 即视为免认证终端。 */
    private boolean isExempt(String mac, String ip) {
        try {
            String nMac = norm(mac);
            String nIp = ip == null ? "" : ip.trim();
            for (Map<String, String> t : exemptMapper.listEnabled()) {
                String tm = t.get("mac"), ti = t.get("ip");
                if (tm != null && !tm.isBlank() && norm(tm).equals(nMac) && !nMac.isEmpty()) return true;
                if (ti != null && !ti.isBlank() && ti.trim().equals(nIp) && !nIp.isEmpty()) return true;
            }
        } catch (Exception e) {
            log.warn("免认证终端查询失败: {}", e.getMessage());
        }
        return false;
    }

    private String norm(String mac) {
        return mac == null ? "" : mac.replaceAll("[^0-9a-fA-F]", "").toLowerCase();
    }

    /** PAP：解密报文口令，与存储凭证比对；CHAP：需要可逆明文。 */
    private boolean verify(RadiusPacket request, RadiusUser user) {
        byte[] papEnc = request.getAttr(RadiusCodes.USER_PASSWORD);
        byte[] chapPwd = request.getAttr(RadiusCodes.CHAP_PASSWORD);
        String plain = resolvePlainPassword(user);

        if (chapPwd != null) {
            // CHAP 必须可逆明文；无则不可校验
            if (plain == null) return false;
            return RadiusCodec.verifyChap(chapPwd, plain, request.getRequestAuthenticator());
        }
        if (papEnc != null) {
            String got = RadiusCodec.decryptPapPassword(papEnc, request.getRequestAuthenticator(), secretService.getSharedSecret());
            if (got == null) return false;
            if (plain != null) return constantTimeEquals(got, plain);
            // 兜底：用 BCrypt 校验（PAP 场景）
            return user.getPasswordHash() != null && bcrypt.matches(got, user.getPasswordHash());
        }
        return false; // 既无 PAP 也无 CHAP
    }

    /** 优先使用可逆密文（支持 CHAP），解密失败返回 null。 */
    private String resolvePlainPassword(RadiusUser user) {
        if (user.getRadiusPasswordCipher() == null) return null;
        try {
            return aesCryptoUtil.decrypt(user.getRadiusPasswordCipher());
        } catch (Exception e) {
            log.warn("RADIUS 可逆口令解密失败 user={}", user.getId());
            return null;
        }
    }

    // ---- 防爆破：失败计数（Redis 故障降级放行，遵循 RedisUtil 约定） ----
    private static final String FAIL_KEY = "nac:radius:fail:";

    private boolean isUserLocked(String username) {
        String v = redisUtil.get(FAIL_KEY + username);
        if (v == null) return false;
        try {
            return Long.parseLong(v) >= props.getFailLockThreshold();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void registerFailure(String username) {
        long n = redisUtil.incr(FAIL_KEY + username, props.getFailLockSeconds());
        if (n >= props.getFailLockThreshold()) {
            log.warn("RADIUS 用户失败达阈值，临时锁定: user={} count={}", mask(username), n);
        }
    }

    private void clearFailures(String username) {
        redisUtil.delete(FAIL_KEY + username);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String mask(String u) {
        if (u == null) return "null";
        if (u.length() <= 2) return "***";
        return u.charAt(0) + "***" + u.charAt(u.length() - 1);
    }

    private static String nz(String s) { return s == null ? "-" : s; }
}
