package com.nac.auth.controller;

import com.nac.auth.entity.IsolationConfig;
import com.nac.auth.entity.IsolationRecord;
import com.nac.auth.service.IsolationConfigService;
import com.nac.auth.service.IsolationRecordService;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 隔离区 / 修复区管理（仅 admin）。
 *
 * <p>下发通道支持 ACL（Filter-Id）与 VLAN 两种，由配置切换；隔离记录由 802.1X 认证链路写入。</p>
 */
@RestController
@RequestMapping("/api/auth/admin/isolation")
public class IsolationController {

    private final IsolationConfigService configService;
    private final IsolationRecordService recordService;

    public IsolationController(IsolationConfigService configService, IsolationRecordService recordService) {
        this.configService = configService;
        this.recordService = recordService;
    }

    @GetMapping("/config")
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<IsolationConfig> config() {
        return Result.success(configService.get());
    }

    @PutMapping("/config")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("修改隔离区配置")
    public Result<Void> saveConfig(@RequestBody IsolationConfig c) {
        configService.save(c);
        return Result.success();
    }

    /** 隔离记录：status=ACTIVE/RELEASED/空(全部)，limit 默认 200。 */
    @GetMapping("/records")
    @RequireRole
    @RateLimit(limit = 120, window = 60)
    public Result<List<IsolationRecord>> records(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "200") int limit) {
        return Result.success(recordService.list(status, limit));
    }

    @GetMapping("/stats")
    @RequireRole
    @RateLimit(limit = 120, window = 60)
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.putAll(recordService.stats());
        IsolationConfig c = configService.get();
        data.put("enabled", c.getEnabled());
        data.put("mode", c.getMode());
        return Result.success(data);
    }

    @PostMapping("/release/{id}")
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    @OperationLog("释放隔离终端")
    public Result<Void> release(@PathVariable Long id, @RequestParam(required = false) String cause) {
        recordService.release(id, cause);
        return Result.success();
    }

    @PostMapping("/remediate/{id}")
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    @OperationLog("终端转入修复区")
    public Result<Map<String, Object>> remediate(@PathVariable Long id) {
        return Result.success(recordService.enterRemediation(id));
    }
}
