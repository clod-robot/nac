package com.nac.log;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NAC 操作日志审计服务启动类。
 */
@SpringBootApplication(scanBasePackages = "com.nac")
public class LogApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogApplication.class, args);
    }
}
