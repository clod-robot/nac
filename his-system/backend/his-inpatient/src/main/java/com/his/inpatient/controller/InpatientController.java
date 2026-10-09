package com.his.inpatient.controller;

import com.his.common.idempotent.Idempotent;
import com.his.common.result.Result;
import com.his.inpatient.entity.Admission;
import com.his.inpatient.service.InpatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inpatient")
public class InpatientController {

    @Autowired private InpatientService service;

    @GetMapping("/beds/free")
    public Result<List<Map<String, Object>>> freeBeds() {
        return Result.ok(service.freeBeds());
    }

    @PostMapping("/admission")
    @Idempotent(key = "'admit:' + #a.patientId + '_' + #a.bedId", expire = 5)
    public Result<Admission> admit(@RequestBody Admission a) {
        return Result.ok(service.admit(a));
    }

    @PostMapping("/discharge/{id}")
    public Result<Void> discharge(@PathVariable Long id, @RequestParam Long bedId) {
        service.discharge(id, bedId);
        return Result.ok();
    }

    @GetMapping("/admitted")
    public Result<List<Admission>> admitted() {
        return Result.ok(service.admitted());
    }
}
