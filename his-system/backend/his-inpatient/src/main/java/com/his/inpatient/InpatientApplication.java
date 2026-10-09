package com.his.inpatient;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.his")
@EnableDiscoveryClient
@MapperScan("com.his.inpatient.mapper")
public class InpatientApplication {
    public static void main(String[] args) {
        SpringApplication.run(InpatientApplication.class, args);
    }
}
