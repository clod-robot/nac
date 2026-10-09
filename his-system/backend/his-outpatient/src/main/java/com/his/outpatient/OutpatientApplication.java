package com.his.outpatient;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.his")
@EnableDiscoveryClient
@MapperScan("com.his.outpatient.mapper")
public class OutpatientApplication {
    public static void main(String[] args) {
        SpringApplication.run(OutpatientApplication.class, args);
    }
}
