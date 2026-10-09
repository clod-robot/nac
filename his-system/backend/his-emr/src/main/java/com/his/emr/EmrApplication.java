package com.his.emr;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.his")
@EnableDiscoveryClient
@MapperScan("com.his.emr.mapper")
public class EmrApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmrApplication.class, args);
    }
}
