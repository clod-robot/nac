package com.nac.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 设置 RADIUS 专用口令请求。明文仅传输一次，服务端加密后不存明文。 */
@Data
public class SetRadiusPasswordRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名过长")
    private String username;

    @NotBlank(message = "口令不能为空")
    @Size(min = 6, max = 64, message = "口令长度需 6-64 位")
    private String radiusPassword;
}
