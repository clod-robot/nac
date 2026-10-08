package com.nac.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {
    private String token;
    private String username;
    private String role;
    private Long userId;
    /** 是否仍在使用默认密码(admin123)，需强制修改 */
    private boolean mustChangePwd;
}
