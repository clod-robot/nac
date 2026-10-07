package com.nac.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NAC 用户与终端管理服务启动类。
 */
@SpringBootApplication(scanBasePackages = "com.nac")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
