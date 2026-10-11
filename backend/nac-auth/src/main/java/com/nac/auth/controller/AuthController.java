package com.nac.auth.controller;

import com.nac.auth.dto.LoginRequest;
import com.nac.auth.dto.LoginVO;
import com.nac.auth.service.AuthService;
import com.nac.auth.service.CaptchaService;
import com.nac.common.context.UserContext;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口：图形验证码、登录、登出、当前身份。
 * 登录态以 HttpOnly Cookie(NAC_TOKEN) 承载，前端不可读，免疫 XSS 窃取；
 * 另下发非 HttpOnly 的 XSRF-TOKEN 供前端回传做 CSRF 防护。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String TOKEN_COOKIE = "NAC_TOKEN";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, CaptchaService captchaService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/captcha")
    @RateLimit(limit = 60, window = 60)
    public Result<CaptchaService.CaptchaVO> captcha() {
        return Result.success(captchaService.generate());
    }

    @PostMapping("/login")
    @RateLimit(limit = 60, window = 60)
    @OperationLog("管理登录")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest req,
                                 HttpServletRequest request, HttpServletResponse response) {
        LoginVO vo = authService.login(req, clientIp(request));
        long maxAge = jwtUtil.getExpireSeconds();
        writeCookie(response, TOKEN_COOKIE, vo.getToken(), true, maxAge, isHttps(request));
        writeCookie(response, XSRF_COOKIE, randomToken(), false, maxAge, isHttps(request));
        vo.setToken(null); // token 仅存于 HttpOnly Cookie，不下发到前端
        return Result.success(vo);
    }

    /** 刷新页面后恢复前端身份（用户名/角色），cookie 由浏览器自动携带 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Map<String, Object> m = new HashMap<>(4);
        m.put("username", UserContext.getUsername());
        m.put("role", UserContext.getRole());
        m.put("userId", UserContext.getUserId());
        m.put("mustChangePwd", false);
        return Result.success(m);
    }

    @PostMapping("/logout")
    @OperationLog("登出")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(UserContext.getUserId());
        clearCookie(response, TOKEN_COOKIE, isHttps(request));
        clearCookie(response, XSRF_COOKIE, isHttps(request));
        return Result.success();
    }

    private void writeCookie(HttpServletResponse resp, String name, String value,
                             boolean httpOnly, long maxAge, boolean secure) {
        resp.addHeader("Set-Cookie", ResponseCookie.from(name, value)
                .path("/")
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite("Lax")
                .maxAge(Duration.ofSeconds(maxAge))
                .build().toString());
    }

    private void clearCookie(HttpServletResponse resp, String name, boolean secure) {
        resp.addHeader("Set-Cookie", ResponseCookie.from(name, "")
                .path("/")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .maxAge(0)
                .build().toString());
    }

    private boolean isHttps(HttpServletRequest req) {
        if ("https".equalsIgnoreCase(req.getScheme())) return true;
        String proto = req.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(proto);
    }

    private String randomToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isEmpty()) return real;
        return req.getRemoteAddr();
    }
}
