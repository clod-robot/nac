package com.nac.common.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置：认证业务独立线程池（默认 2×CPU 核心），避免与 web 容器线程争抢。
 */
@EnableAsync
@Configuration
public class ThreadPoolConfig {

    @Value("${nac.thread.auth-core-multiplier:2}")
    private int coreMultiplier;

    @Bean("authExecutor")
    @ConditionalOnMissingBean(name = "authExecutor")
    public Executor authExecutor() {
        int cpu = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(cpu * coreMultiplier);
        exec.setMaxPoolSize(cpu * coreMultiplier * 2);
        exec.setQueueCapacity(2000);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix("nac-auth-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }
}
