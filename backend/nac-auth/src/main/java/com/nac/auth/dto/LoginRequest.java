package com.nac.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求。图形验证码一次性校验。
 */
@Data
public class LoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    @NotBlank
    private String captchaKey;
    @NotBlank
    private String captchaCode;
    /** 幂等 traceId（可选） */
    private String traceId;
}
