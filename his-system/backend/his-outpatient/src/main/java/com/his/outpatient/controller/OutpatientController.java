package com.his.outpatient.controller;

import com.his.common.ratelimit.RateLimit;
import com.his.common.result.Result;
import com.his.outpatient.entity.*;
import com.his.outpatient.service.OutpatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outpatient")
public class OutpatientController {

    @Autowired private OutpatientService service;

    /** 可挂号排班列表 */
    @GetMapping("/schedules")
    public Result<List<DoctorSchedule>> schedules(@RequestParam String date) {
        return Result.ok(service.listSchedules(date));
    }

    /** 挂号（幂等防重复 + 并发防超卖） */
    @PostMapping("/registration")
    @RateLimit(limit = 30, window = 60)
    public Result<Registration> register(@RequestBody Registration reg) {
        return Result.ok(service.register(reg));
    }

    /** 医生开立处方 */
    @PostMapping("/prescription")
    @RateLimit(limit = 60, window = 60)
    public Result<Prescription> createPrescription(@RequestBody Map<String, Object> body) {
        // 简化：body 含 prescription 主信息 + items 列表
        return Result.ok(null);
    }

    @GetMapping("/prescription/{id}")
    public Result<Prescription> prescription(@PathVariable Long id) {
        return Result.ok(null);
    }

    @GetMapping("/prescription/{id}/items")
    public Result<List<PrescriptionItem>> items(@PathVariable Long id) {
        return Result.ok(service.listPrescriptionItems(id));
    }

    /** 缴费回调：处方置为待发药（药房测试闭环用） */
    @PostMapping("/prescription/{id}/pay")
    public Result<Void> pay(@PathVariable Long id) {
        service.markPaid(id);
        return Result.ok();
    }
}
