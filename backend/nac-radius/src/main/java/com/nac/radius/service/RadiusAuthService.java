package com.nac.radius.service;

import com.nac.common.redis.RedisUtil;
import com.nac.common.security.AesCryptoUtil;
import com.nac.radius.config.RadiusProperties;
import com.nac.radius.entity.RadiusUser;
import com.nac.radius.mapper.RadiusUserMapper;
import com.nac.radius.packet.RadiusCodes;
import com.nac.radius.packet.RadiusCodec;
import com.nac.radius.packet.RadiusPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * RADIUS 认证（RFC 2865）：支持 PAP 与 CHAP。
 * 安全策略：用户禁用/锁定拒绝；失败计数 Redis 防爆破；明文口令不落日志。
 */
@Slf4j
@Service
public class RadiusAuthService {

    private final RadiusUserMapper userMapper;
    private final RedisUtil redisUtil;
    private final AesCryptoUtil aesCryptoUtil;
    private final RadiusProperties props;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public RadiusAuthService(RadiusUserMapper userMapper, RedisUtil redisUtil,
                             AesCryptoUtil aesCryptoUtil, RadiusProperties props) {
        this.userMapper = userMapper;
        this.redisUtil = redisUtil;
        this.aesCryptoUtil = aesCryptoUtil;
        this.props = props;
    }

    /** 处理 Access-Request，返回响应报文（Accept/Reject），异常时返回 Reject。 */
    public RadiusPacket authenticate(RadiusPacket request) {
        String username = request.getString(RadiusCodes.USER_NAME);
        RadiusPacket reject = new RadiusPacket(RadiusCodes.ACCESS_REJECT, request.getIdentifier(), null);

        if (isBlank(username)) {
            reject.addString(RadiusCodes.REPLY_MESSAGE, "missing username");
            return reject;
        }
        // 防爆破：该用户是否处于失败锁定窗口
        if (isUserLocked(username)) {
            log.info("RADIUS 认证拒绝(锁定中): user={}", mask(username));
            reject.addString(RadiusCodes.REPLY_MESSAGE, "account temporarily locked");
            return reject;
        }

        RadiusUser user;
        try {
            user = userMapper.selectByUsername(username);
        } catch (Exception e) {
            log.error("RADIUS 查询用户失败: {}", e.getMessage());
            reject.addString(RadiusCodes.REPLY_MESSAGE, "server error");
            return reject;
        }
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            registerFailure(username);
            reject.addString(RadiusCodes.REPLY_MESSAGE, "invalid credentials");
            return reject;
        }

        boolean ok = verify(request, user);
        if (!ok) {
            registerFailure(username);
            log.info("RADIUS 认证失败: user={}", mask(username));
            reject.addString(RadiusCodes.REPLY_MESSAGE, "invalid credentials");
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
        log.info("RADIUS 认证成功: user={} nas={}", mask(username), request.getString(RadiusCodes.NAS_IP_ADDRESS));
        return accept;
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
            String got = RadiusCodec.decryptPapPassword(papEnc, request.getRequestAuthenticator(), props.getSharedSecret());
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
}
