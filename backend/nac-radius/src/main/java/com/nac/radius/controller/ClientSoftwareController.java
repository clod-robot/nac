package com.nac.radius.controller;

import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.radius.dto.SoftwareReportReq;
import com.nac.radius.service.SoftwareBaselineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 终端客户端软件基线心跳上报（终端直连 8083，不经网关）。
 *
 * <p>安全：不依赖网关信任头，改用 HMAC-SHA256 共享密钥 + nonce 防重放 + 时间窗；
 * 上报结果决定该 MAC 在 Redis 中的合规标记，Portal 与 802.1X 认证均以此为准。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/client")
public class ClientSoftwareController {

    private final SoftwareBaselineService baselineService;

    public ClientSoftwareController(SoftwareBaselineService baselineService) {
        this.baselineService = baselineService;
    }

    /** 下发当前策略要求的软件名（不含哈希），供客户端采集与心跳使用。 */
    @GetMapping("/software-policy")
    @RateLimit(limit = 120, window = 60)
    public Result<Map<String, Object>> policy() {
        boolean enabled = baselineService.enforced();
        List<String> names = enabled ? baselineService.challengeNames() : List.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", enabled);
        data.put("names", names);
        return Result.success(data);
    }

    /** 上报运行中软件清单；返回是否满足软件基线（客户端可据此提示用户）。 */
    @PostMapping("/software-report")
    @RateLimit(limit = 120, window = 60)
    public Result<Map<String, Object>> report(@RequestBody SoftwareReportReq req) {
        SoftwareBaselineService.Result r = baselineService.handleReport(req);
        if (!r.ok()) {
            log.info("软件基线上报不合规: mac={} 原因={}", req == null ? null : req.getMac(), r.message());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", r.ok());
        data.put("message", r.message());
        data.put("missing", r.missing());
        data.put("reported", r.reported());
        return Result.success(data);
    }
}
