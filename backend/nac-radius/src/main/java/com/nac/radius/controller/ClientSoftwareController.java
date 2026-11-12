package com.nac.radius.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.Result;
import com.nac.common.software.SoftwareBaseline;
import com.nac.radius.dto.SoftwareReportReq;
import com.nac.radius.service.SoftwareBaselineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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

    /** 实时明细键（供管理端「在线终端合规状态」展示），TTL 略大于心跳周期。 */
    private static final String DETAIL_KEY_PREFIX = "nac:swa:detail:";
    private static final int DETAIL_TTL = 120;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SoftwareBaselineService baselineService;
    private final RedisUtil redis;

    public ClientSoftwareController(SoftwareBaselineService baselineService, RedisUtil redis) {
        this.baselineService = baselineService;
        this.redis = redis;
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
        recordDetail(req, r);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", r.ok());
        data.put("message", r.message());
        data.put("missing", r.missing());
        data.put("reported", r.reported());
        return Result.success(data);
    }

    /** 写实时明细（管理端看板读取）：合规状态、缺失软件、最近上报时间。 */
    private void recordDetail(SoftwareReportReq req, SoftwareBaselineService.Result r) {
        if (req == null) return;
        try {
            String mac = SoftwareBaseline.normMac(req.getMac());
            if (mac.isEmpty()) return;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("ts", System.currentTimeMillis() / 1000);
            d.put("ip", req.getIp());
            d.put("hostname", req.getHostname());
            d.put("clientVersion", req.getClientVersion());
            d.put("compliant", r.ok());
            d.put("missing", r.missing());
            List<String> names = new ArrayList<>();
            if (req.getSoftware() != null) {
                for (SoftwareReportReq.SoftwareItem it : req.getSoftware()) names.add(it.getName());
            }
            d.put("names", names);
            redis.set(DETAIL_KEY_PREFIX + mac, MAPPER.writeValueAsString(d), DETAIL_TTL);
        } catch (Exception e) {
            log.warn("写软件基线实时明细失败: {}", e.getMessage());
        }
    }
}
