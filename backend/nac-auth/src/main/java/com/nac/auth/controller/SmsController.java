package com.nac.auth.controller;

import com.nac.auth.dto.LoginVO;
import com.nac.auth.service.SmsService;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

/**
 * 短信接口：发送验证码（图形验证码前置）、校验验证码、短信登录。
 */
@RestController
@RequestMapping("/api/auth/sms")
public class SmsController {

    private final SmsService smsService;

    public SmsController(SmsService smsService) {
        this.smsService = smsService;
    }

    @Data
    public static class SendReq {
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$")
        private String phone;
        @NotBlank private String captchaKey;
        @NotBlank private String captchaCode;
        private String bizType = "login";
    }

    @Data
    public static class VerifyReq {
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$")
        private String phone;
        @NotBlank private String code;
    }

    @PostMapping("/send")
    @RateLimit(limit = 30, window = 60)
    public Result<Void> send(@RequestBody SendReq req, HttpServletRequest request) {
        smsService.sendCode(req.getPhone(), req.getCaptchaKey(), req.getCaptchaCode(), req.getBizType(), clientIp(request));
        return Result.success();
    }

    @PostMapping("/verify")
    @RateLimit(limit = 60, window = 60)
    public Result<Boolean> verify(@RequestBody VerifyReq req) {
        return Result.success(smsService.verifyCode(req.getPhone(), req.getCode()));
    }

    @PostMapping("/login")
    @RateLimit(limit = 30, window = 60)
    public Result<LoginVO> login(@RequestBody VerifyReq req, HttpServletRequest request) {
        return Result.success(smsService.loginBySms(req.getPhone(), req.getCode(), clientIp(request)));
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isEmpty()) return real;
        return req.getRemoteAddr();
    }
}
