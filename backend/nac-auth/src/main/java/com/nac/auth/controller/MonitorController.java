package com.nac.auth.controller;

import com.nac.auth.dto.MonitorSnapshot;
import com.nac.auth.mapper.AuthLogMapper;
import com.nac.auth.service.MonitorService;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘服务器监控：
 *  - GET /snapshot：进入页面时一次性拉取全量（CPU/内存/网卡静态信息 + 服务状态）
 *  - GET /stats：今日认证/异常拦截/短信发送 + 认证方式分布 + 近7日趋势（真实数据）
 *  - GET /stream：SSE 持续推送使用率与网卡上传/下载速率（浏览器 EventSource，token 走 query）
 */
@RestController
@RequestMapping("/api/auth/monitor")
public class MonitorController {

    private final MonitorService monitorService;
    private final AuthLogMapper authLogMapper;

    public MonitorController(MonitorService monitorService, AuthLogMapper authLogMapper) {
        this.monitorService = monitorService;
        this.authLogMapper = authLogMapper;
    }

    @GetMapping("/snapshot")
    @RateLimit(limit = 60, window = 60)
    public Result<MonitorSnapshot> snapshot() {
        return Result.success(monitorService.snapshot());
    }

    @GetMapping("/stats")
    @RateLimit(limit = 60, window = 60)
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("todayAuth", authLogMapper.countToday());
        data.put("blocked", authLogMapper.countTodayFail());
        data.put("smsCnt", authLogMapper.countTodayByType("portal"));

        List<Map<String, Object>> pie = new ArrayList<>();
        for (Map<String, Object> r : authLogMapper.statTypeToday()) {
            String type = String.valueOf(r.get("name"));
            pie.add(Map.of("name", typeLabel(type), "value", ((Number) r.get("value")).longValue()));
        }
        data.put("pie", pie);
        data.put("trend", authLogMapper.statTrend7d());
        return Result.success(data);
    }

    private String typeLabel(String type) {
        if ("portal".equals(type)) return "短信验证码";
        if ("radius".equals(type)) return "802.1X";
        if ("sms".equals(type)) return "短信登录";
        return type;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return monitorService.subscribe();
    }
}
