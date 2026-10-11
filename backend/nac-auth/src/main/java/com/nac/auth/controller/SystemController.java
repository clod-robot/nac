package com.nac.auth.controller;

import com.nac.auth.service.InternalSecretService;
import com.nac.auth.service.SystemService;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 系统信息：服务器时间 / 时区 / NTP 服务器（读取与修改）。 */
@RestController
@RequestMapping("/api/auth/system")
public class SystemController {

    private final SystemService systemService;
    private final InternalSecretService internalSecretService;

    public SystemController(SystemService systemService, InternalSecretService internalSecretService) {
        this.systemService = systemService;
        this.internalSecretService = internalSecretService;
    }

    @GetMapping("/info")
    @RequireRole
    @RateLimit(limit = 120, window = 60)
    public Result<SystemService.SystemInfo> info() {
        return Result.success(systemService.info());
    }

    @PutMapping("/ntp")
    @RequireRole
    @RateLimit(limit = 10, window = 60)
    @OperationLog("修改 NTP 服务器")
    public Result<Void> setNtp(@RequestBody Map<String, String> body) {
        systemService.setNtp(body.get("ntp"));
        return Result.success();
    }

    /** 网关内部签名密钥状态：仅返回是否默认，绝不回显明文 */
    @GetMapping("/secret/status")
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<Map<String, Object>> secretStatus() {
        return Result.success(Map.of("isDefault", internalSecretService.isDefault()));
    }

    /** 生成 32 位高强度随机密钥（不落库、不审计，仅返回给前端填入） */
    @GetMapping("/secret/generate")
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<Map<String, String>> secretGenerate() {
        return Result.success(Map.of("secret", internalSecretService.generate()));
    }

    /** 保存网关内部签名密钥（强度校验，落库 + 刷新缓存；需重启各服务生效） */
    @PutMapping("/secret")
    @RequireRole
    @RateLimit(limit = 20, window = 60)
    @OperationLog("修改网关内部签名密钥")
    public Result<Void> setSecret(@RequestBody Map<String, String> body) {
        internalSecretService.save(body.get("secret"));
        return Result.success();
    }
}
