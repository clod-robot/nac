package com.nac.auth.controller;

import com.nac.auth.entity.SoftwarePolicy;
import com.nac.auth.service.SoftwarePolicyService;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 终端软件基线策略管理（仅 admin）。
 * 命中规则的终端必须运行指定软件（可选校验可执行文件哈希）才能通过 Portal / 802.1X 认证。
 */
@RestController
@RequestMapping("/api/auth/admin/software-policies")
public class SoftwarePolicyController {

    private final SoftwarePolicyService service;

    public SoftwarePolicyController(SoftwarePolicyService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<List<SoftwarePolicy>> list() {
        return Result.success(service.list());
    }

    @PostMapping
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("新增软件基线策略")
    public Result<Void> create(@RequestBody SoftwarePolicy p) {
        service.create(p);
        return Result.success();
    }

    @PutMapping
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("修改软件基线策略")
    public Result<Void> update(@RequestBody SoftwarePolicy p) {
        service.update(p);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("删除软件基线策略")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    /** 当前生效状态：开关、判定模式、必需软件项（便于管理员确认是否已真正阻断）。 */
    @GetMapping("/status")
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<Map<String, Object>> status() {
        return Result.success(Map.of(
                "enabled", service.isEnabled(),
                "enforced", service.enforced(),
                "mode", service.mode(),
                "required", service.requiredNames()));
    }
}
