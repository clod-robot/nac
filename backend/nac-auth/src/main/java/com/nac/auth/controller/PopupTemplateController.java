package com.nac.auth.controller;

import com.nac.auth.entity.PopupTemplate;
import com.nac.auth.service.PopupTemplateService;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import org.springframework.web.bind.annotation.*;

/**
 * 终端合规弹窗全局模板（仅 admin）。
 */
@RestController
@RequestMapping("/api/auth/admin/popup-template")
public class PopupTemplateController {

    private final PopupTemplateService service;

    public PopupTemplateController(PopupTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<PopupTemplate> get() {
        return Result.success(service.get());
    }

    @PutMapping
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("修改终端合规弹窗模板")
    public Result<Void> save(@RequestBody PopupTemplate t) {
        t.setId(1L);
        service.save(t);
        return Result.success();
    }
}
