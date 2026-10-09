package com.his.auth.controller;

import com.his.auth.entity.SysUser;
import com.his.auth.mapper.SysUserMapper;
import com.his.auth.service.CaptchaService;
import com.his.auth.service.LoginAttemptService;
import com.his.common.constant.RedisKeyConstants;
import com.his.common.context.UserContext;
import com.his.common.ratelimit.RateLimit;
import com.his.common.redis.RedisUtil;
import com.his.common.result.Result;
import com.his.common.result.ResultCode;
import com.his.common.security.JwtUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private SysUserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private RedisUtil redisUtil;
    @Autowired private CaptchaService captchaService;
    @Autowired private LoginAttemptService loginAttemptService;

    @Data
    public static class LoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;
        @NotBlank private String captchaKey;
        @NotBlank private String captchaCode;
    }

    /** 获取图形验证码：60次/分钟/IP 防刷 */
    @GetMapping("/captcha")
    @RateLimit(limit = 60, window = 60)
    public Result<Map<String, String>> captcha() {
        String[] r = captchaService.generate();
        Map<String, String> map = new HashMap<>(2);
        map.put("captchaKey", r[0]);
        map.put("image", r[1]);
        return Result.ok(map);
    }

    /** 登录：60次/分钟/IP 防刷 + 图形验证码 + 防爆破 */
    @PostMapping("/login")
    @RateLimit(limit = 60, window = 60)
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        String ip = clientIp(request);
        if (loginAttemptService.isIpLocked(ip)) {
            return Result.fail(ResultCode.LOGIN_LOCKED.getCode(), "该 IP 登录失败次数过多，请 15 分钟后再试");
        }
        if (loginAttemptService.isUserLocked(req.getUsername())) {
            return Result.fail(ResultCode.LOGIN_LOCKED.getCode(), "账号已锁定，请 30 分钟后再试");
        }
        if (!captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode())) {
            loginAttemptService.recordFail(ip, req.getUsername());
            return Result.fail(ResultCode.CAPTCHA_ERROR);
        }
        SysUser user = userMapper.selectByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            loginAttemptService.recordFail(ip, req.getUsername());
            return Result.fail(ResultCode.USERNAME_PASSWORD_ERROR);
        }
        loginAttemptService.recordSuccess(ip, req.getUsername());

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        // token 存 Redis 白名单，登出即删（即时失效）
        redisUtil.set(RedisKeyConstants.TOKEN_PREFIX + user.getId(), token, jwtUtil.getExpireSeconds());

        Map<String, Object> data = new HashMap<>(4);
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("role", user.getRole());
        log.info("用户登录成功: {} ({})", user.getUsername(), user.getRole());
        return Result.ok(data);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        Long uid = UserContext.getUserId();
        if (uid != null) redisUtil.delete(RedisKeyConstants.TOKEN_PREFIX + uid);
        return Result.ok();
    }

    @GetMapping("/current")
    public Result<Map<String, Object>> current() {
        Long uid = UserContext.getUserId();
        if (uid == null) return Result.fail(ResultCode.UNAUTHORIZED);
        SysUser u = userMapper.selectById(uid);
        Map<String, Object> data = new HashMap<>(4);
        data.put("userId", u.getId());
        data.put("username", u.getUsername());
        data.put("realName", u.getRealName());
        data.put("role", u.getRole());
        return Result.ok(data);
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isEmpty()) return real;
        return req.getRemoteAddr();
    }
}
