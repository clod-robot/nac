package com.nac.radius;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NAC RADIUS 认证计费服务启动类（Netty UDP，一期仅骨架）。
 */
@SpringBootApplication(scanBasePackages = "com.nac")
public class RadiusApplication {
    public static void main(String[] args) {
        SpringApplication.run(RadiusApplication.class, args);
    }
}
