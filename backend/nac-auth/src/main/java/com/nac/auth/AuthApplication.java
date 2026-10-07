package com.nac.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NAC 认证服务启动类：管理登录、短信认证、Portal 认证、防爆破、防刷。
 */
@SpringBootApplication(scanBasePackages = "com.nac")
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
