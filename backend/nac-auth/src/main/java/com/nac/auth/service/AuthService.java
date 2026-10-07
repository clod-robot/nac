package com.nac.auth.service;

import com.nac.auth.dto.LoginRequest;
import com.nac.auth.dto.LoginVO;
import com.nac.auth.entity.AuthLog;
import com.nac.auth.entity.SysUser;
import com.nac.auth.mapper.AuthLogMapper;
import com.nac.auth.mapper.SysUserMapper;
import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.exception.BusinessException;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.ResultCode;
import com.nac.common.security.JwtUtil;
import com.nac.common.syslog.SyslogForwarder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：账号密码登录（图形验证码 + 防爆破 + 失败锁定）、登出即时失效。
 */
@Slf4j
@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final AuthLogMapper authLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;
    private final SyslogForwarder syslogForwarder;

    public AuthService(SysUserMapper userMapper, AuthLogMapper authLogMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       RedisUtil redisUtil, CaptchaService captchaService, LoginAttemptService loginAttemptService,
                       SyslogForwarder syslogForwarder) {
        this.userMapper = userMapper;
        this.authLogMapper = authLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
        this.captchaService = captchaService;
        this.loginAttemptService = loginAttemptService;
        this.syslogForwarder = syslogForwarder;
    }

    public LoginVO login(LoginRequest req, String ip) {
        // 幂等：同 traceId 10s 内防重复提交
        if (req.getTraceId() != null && !req.getTraceId().isEmpty()) {
            if (!redisUtil.setIfAbsent(RedisKeyConstants.LOGIN_IDEMPOTENT_PREFIX + req.getTraceId(), 10)) {
                throw new BusinessException(ResultCode.IDEMPOTENT_REPEAT);
            }
        }
        // 图形验证码一次性校验
        if (!captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode())) {
            throw new BusinessException(ResultCode.CAPTCHA_INVALID);
        }
        // 锁定检查
        if (loginAttemptService.isIpLocked(ip)) {
            throw new BusinessException(ResultCode.IP_LOCKED);
        }
        if (loginAttemptService.isUserLocked(req.getUsername())) {
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
        }
        SysUser user = userMapper.selectByUsername(req.getUsername());
        boolean ok = user != null && user.getStatus() != null && user.getStatus() == 1
                && passwordEncoder.matches(req.getPassword(), user.getPasswordHash());
        if (!ok) {
            loginAttemptService.recordFail(ip, req.getUsername());
            recordAuth("login", req.getUsername(), null, null, ip, 0, "用户名或密码错误");
            // 统一错误信息，防止账号枚举
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        loginAttemptService.clear(ip, req.getUsername());
        recordAuth("login", user.getUsername(), null, null, ip, 1, "登录成功");

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRoleCode());
        redisUtil.set(RedisKeyConstants.TOKEN_PREFIX + user.getId(), token, (int) jwtUtil.getExpireSeconds());
        log.info("登录成功: user={}, ip={}", user.getUsername(), ip);
        return new LoginVO(token, user.getUsername(), user.getRoleCode(), user.getId());
    }

    public void logout(Long userId) {
        if (userId != null) {
            redisUtil.delete(RedisKeyConstants.TOKEN_PREFIX + userId);
        }
    }

    /** 写认证日志，任何异常不影响主流程 */
    private void recordAuth(String type, String username, String phone, String mac, String ip, int result, String msg) {
        try {
            AuthLog l = new AuthLog();
            l.setAuthType(type);
            l.setUsername(username);
            l.setPhone(phone);
            l.setMac(mac);
            l.setIp(ip);
            l.setResult(result);
            l.setMessage(msg);
            authLogMapper.insert(l);
            syslogForwarder.forwardAuthLog("authLog type=" + type + " user=" + nz(username)
                    + " phone=" + nz(phone) + " mac=" + nz(mac) + " ip=" + nz(ip)
                    + " result=" + result + " msg=" + nz(msg));
        } catch (Exception e) {
            log.warn("写认证日志失败: {}", e.getMessage());
        }
    }

    private static String nz(String s) { return s == null ? "-" : s; }
}
