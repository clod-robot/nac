package com.nac.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NAC 网关启动类（WebFlux）：路由、鉴权、限流、HMAC 签名、安全响应头。
 */
@SpringBootApplication(scanBasePackages = "com.nac")
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
