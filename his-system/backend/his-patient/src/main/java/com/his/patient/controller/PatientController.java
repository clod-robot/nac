package com.his.patient.controller;

import com.his.common.result.Result;
import com.his.common.ratelimit.RateLimit;
import com.his.patient.entity.Patient;
import com.his.patient.service.PatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patient")
public class PatientController {

    @Autowired private PatientService patientService;

    @GetMapping("/list")
    @RateLimit(limit = 120, window = 60)
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Patient> list = patientService.page(size, offset);
        Map<String, Object> data = new HashMap<>(2);
        data.put("list", list);
        data.put("total", patientService.count());
        return Result.ok(data);
    }

    @GetMapping("/{id}")
    public Result<Patient> detail(@PathVariable Long id) {
        return Result.ok(patientService.detail(id));
    }

    @PostMapping
    public Result<Patient> create(@RequestBody Patient patient) {
        return Result.ok(patientService.create(patient));
    }

    @PutMapping
    public Result<Patient> update(@RequestBody Patient patient) {
        return Result.ok(patientService.update(patient));
    }
}
