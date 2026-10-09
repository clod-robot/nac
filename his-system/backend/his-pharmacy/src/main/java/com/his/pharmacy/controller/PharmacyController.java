package com.his.pharmacy.controller;

import com.his.common.ratelimit.RateLimit;
import com.his.common.result.Result;
import com.his.common.security.RequireRole;
import com.his.pharmacy.service.PharmacyService;
import com.his.pharmacy.vo.PrescriptionView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 药房发药工作台接口。
 * 权限：网关层校验 token，角色 pharmacist 在前端控制按钮可见性；
 * 写操作（发药/审核）建议后端再加 @PreAuthorize 注解（Spring Security 接入时）。
 */
@RestController
@RequestMapping("/api/pharmacy")
public class PharmacyController {

    @Autowired private PharmacyService pharmacyService;

    /** 待发药处方列表 */
    @GetMapping("/prescriptions")
    @RateLimit(limit = 120, window = 60)
    public Result<List<PrescriptionView>> pendingList(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(pharmacyService.listPendingDispense(limit));
    }

    /** 处方详情 + 库存预检查 */
    @GetMapping("/prescription/{id}")
    public Result<PrescriptionView> detail(@PathVariable Long id) {
        return Result.ok(pharmacyService.prescriptionDetail(id));
    }

    /** 处方审核（仅药师/管理员） */
    @PostMapping("/prescription/{id}/review")
    @RateLimit(limit = 60, window = 60)
    @RequireRole({"pharmacist", "admin"})
    public Result<Void> review(@PathVariable Long id,
                               @RequestParam boolean approved,
                               @RequestParam(required = false) String opinion) {
        pharmacyService.review(id, approved, opinion);
        return Result.ok();
    }

    /** 发药（幂等 + 乐观锁库存扣减 + 发药流水；仅药师/管理员） */
    @PostMapping("/dispense/{prescriptionId}")
    @RateLimit(limit = 60, window = 60)
    @RequireRole({"pharmacist", "admin"})
    public Result<Void> dispense(@PathVariable Long prescriptionId,
                                 @RequestParam(defaultValue = "1") String windowNo) {
        pharmacyService.dispense(prescriptionId, windowNo);
        return Result.ok();
    }

    /** 发药统计（ECharts 数据） */
    @GetMapping("/stats")
    @RateLimit(limit = 60, window = 60)
    public Result<Map<String, Object>> stats(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(pharmacyService.stats(days));
    }

    /** 药品目录 */
    @GetMapping("/drugs")
    public Result<Map<String, Object>> drugs(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(pharmacyService.listDrugs(page, size));
    }
}
