package com.nac.auth.controller;

import com.nac.auth.dto.LoginRequest;
import com.nac.auth.dto.LoginVO;
import com.nac.auth.service.AuthService;
import com.nac.auth.service.CaptchaService;
import com.nac.common.context.UserContext;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口：图形验证码、登录、登出。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    @RateLimit(limit = 60, window = 60)
    public Result<CaptchaService.CaptchaVO> captcha() {
        return Result.success(captchaService.generate());
    }

    @PostMapping("/login")
    @RateLimit(limit = 60, window = 60)
    @OperationLog("管理登录")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        return Result.success(authService.login(req, clientIp(request)));
    }

    @PostMapping("/logout")
    @OperationLog("登出")
    public Result<Void> logout() {
        authService.logout(UserContext.getUserId());
        return Result.success();
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isEmpty()) return real;
        return req.getRemoteAddr();
    }
}
