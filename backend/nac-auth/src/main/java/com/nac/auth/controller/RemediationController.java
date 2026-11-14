package com.nac.auth.controller;

import com.nac.auth.entity.AvLibrary;
import com.nac.auth.entity.PatchLibrary;
import com.nac.auth.entity.RemediationRule;
import com.nac.auth.entity.RemediationTask;
import com.nac.auth.service.RemediationService;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 修复编排管理（仅 admin）：补丁库、病毒库、修复规则与任务。
 */
@RestController
@RequestMapping("/api/auth/admin/remediation")
public class RemediationController {

    private final RemediationService service;

    public RemediationController(RemediationService service) {
        this.service = service;
    }

    // ---------------- 补丁库 ----------------

    @GetMapping("/patches")
    @RequireRole
    @RateLimit(limit = 120, window = 60)
    public Result<List<PatchLibrary>> patches() {
        return Result.success(service.patches());
    }

    @PostMapping("/patches")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("新增补丁")
    public Result<Void> createPatch(@RequestBody PatchLibrary p) {
        p.setId(null);
        service.savePatch(p);
        return Result.success();
    }

    @PutMapping("/patches")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("修改补丁")
    public Result<Void> updatePatch(@RequestBody PatchLibrary p) {
        service.savePatch(p);
        return Result.success();
    }

    @DeleteMapping("/patches/{id}")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("删除补丁")
    public Result<Void> deletePatch(@PathVariable Long id) {
        service.deletePatch(id);
        return Result.success();
    }

    // ---------------- 病毒库 ----------------

    @GetMapping("/avs")
    @RequireRole
    @RateLimit(limit = 120, window = 60)
    public Result<List<AvLibrary>> avs() {
        return Result.success(service.avs());
    }

    @PostMapping("/avs")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("新增病毒库")
    public Result<Void> createAv(@RequestBody AvLibrary a) {
        a.setId(null);
        service.saveAv(a);
        return Result.success();
    }

    @PutMapping("/avs")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("修改病毒库")
    public Result<Void> updateAv(@RequestBody AvLibrary a) {
        service.saveAv(a);
        return Result.success();
    }

    @DeleteMapping("/avs/{id}")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("删除病毒库")
    public Result<Void> deleteAv(@PathVariable Long id) {
        service.deleteAv(id);
        return Result.success();
    }

    // ---------------- 修复规则 ----------------

    @GetMapping("/rules")
    @RequireRole
    @RateLimit(limit = 120, window = 60)
    public Result<List<RemediationRule>> rules() {
        return Result.success(service.rules());
    }

    @PostMapping("/rules")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("新增修复规则")
    public Result<Void> createRule(@RequestBody RemediationRule r) {
        r.setId(null);
        service.saveRule(r);
        return Result.success();
    }

    @PutMapping("/rules")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("修改修复规则")
    public Result<Void> updateRule(@RequestBody RemediationRule r) {
        service.saveRule(r);
        return Result.success();
    }

    @DeleteMapping("/rules/{id}")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("删除修复规则")
    public Result<Void> deleteRule(@PathVariable Long id) {
        service.deleteRule(id);
        return Result.success();
    }

    // ---------------- 修复任务 ----------------

    @GetMapping("/tasks")
    @RequireRole
    @RateLimit(limit = 120, window = 60)
    public Result<List<RemediationTask>> tasks(@RequestParam(required = false) String mac,
                                               @RequestParam(required = false) Long recordId) {
        if (recordId != null) return Result.success(service.tasksByRecord(recordId));
        if (mac != null && !mac.isBlank()) return Result.success(service.tasksByMac(mac));
        return Result.success(List.of());
    }
}
