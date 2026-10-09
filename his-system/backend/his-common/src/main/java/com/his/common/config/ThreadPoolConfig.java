package com.his.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 业务线程池：认证、发药扣库存、Kafka 异步落库等耗时操作从请求线程剥离，
 * 提升高并发下吞吐量（参考 NAC 项目 lessons learned：单线程瓶颈）。
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean("bizExecutor")
    public Executor bizExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cpu = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(cpu * 2);
        executor.setMaxPoolSize(cpu * 4);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("his-biz-");
        // 队列满时由调用线程执行，形成背压，避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
