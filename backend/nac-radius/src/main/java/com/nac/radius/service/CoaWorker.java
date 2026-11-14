package com.nac.radius.service;

import com.nac.common.coa.CoaProtocol;
import com.nac.common.coa.CoaQueue;
import com.nac.common.redis.RedisUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CoA 命令消费者：单线程 + 阻塞出队（BRPOP），保证同一终端的命令按入队顺序执行，
 * 且不占用认证/心跳线程。线程异常自动重启循环，Redis 故障时退避重试。
 */
@Slf4j
@Component
public class CoaWorker {

    private final RedisUtil redis;
    private final CoaService coaService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "coa-worker");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running = true;

    public CoaWorker(RedisUtil redis, CoaService coaService) {
        this.redis = redis;
        this.coaService = coaService;
    }

    @PostConstruct
    public void start() {
        executor.submit(this::loop);
        log.info("CoA 命令消费线程已启动: queue={}", CoaProtocol.QUEUE_KEY);
    }

    @PreDestroy
    public void stop() {
        running = false;
        executor.shutdownNow();
    }

    private void loop() {
        while (running) {
            try {
                String raw = redis.brpop(CoaProtocol.QUEUE_KEY, 2);
                if (raw == null) {
                    // 空闲或 Redis 异常：短暂退避，避免异常时忙轮询
                    Thread.sleep(200);
                    continue;
                }
                CoaProtocol.Command c = CoaQueue.decode(raw);
                if (c == null) continue;
                String result = coaService.execute(c, false);
                log.debug("CoA 命令完成: mac={} action={} zone={} result={}",
                        c.mac(), c.action(), c.zone(), result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("CoA 消费异常: {}", e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
