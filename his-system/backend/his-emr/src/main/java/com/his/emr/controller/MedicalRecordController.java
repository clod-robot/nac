package com.his.emr.controller;

import com.his.common.context.UserContext;
import com.his.common.result.Result;
import com.his.emr.entity.MedicalRecord;
import com.his.emr.service.MedicalRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emr")
public class MedicalRecordController {

    @Autowired private MedicalRecordService service;

    @PostMapping
    public Result<MedicalRecord> create(@RequestBody MedicalRecord record) {
        return Result.ok(service.create(record));
    }

    @PutMapping
    public Result<MedicalRecord> update(@RequestBody MedicalRecord record) {
        return Result.ok(service.update(record));
    }

    @GetMapping("/patient/{patientId}")
    public Result<List<MedicalRecord>> list(@PathVariable Long patientId) {
        return Result.ok(service.listByPatient(patientId));
    }

    /** OnlyOffice 编辑器配置 */
    @GetMapping("/{id}/editor-config")
    public Result<Map<String, Object>> editorConfig(@PathVariable Long id) {
        return Result.ok(service.onlyOfficeConfig(id, UserContext.getUsername()));
    }

    /** OnlyOffice 保存回调（开放给文档服务器内网调用） */
    @PostMapping("/onlyoffice/callback")
    public Result<Void> callback(@RequestParam String key, @RequestParam String url, @RequestParam int status) {
        service.onlyOfficeCallback(key, url, status);
        return Result.ok();
    }
}
