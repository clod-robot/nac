package com.nac.auth.controller;

import com.nac.auth.dto.MonitorSnapshot;
import com.nac.auth.service.MonitorService;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 仪表盘服务器监控：
 *  - GET /snapshot：进入页面时一次性拉取全量（CPU/内存/网卡静态信息 + 服务状态）
 *  - GET /stream：SSE 持续推送使用率与网卡上传/下载速率（浏览器 EventSource，token 走 query）
 */
@RestController
@RequestMapping("/api/auth/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/snapshot")
    @RateLimit(limit = 60, window = 60)
    public Result<MonitorSnapshot> snapshot() {
        return Result.success(monitorService.snapshot());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return monitorService.subscribe();
    }
}
