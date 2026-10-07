package com.nac.auth.controller;

import com.nac.auth.dto.SmsConfig;
import com.nac.auth.service.SmsConfigService;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import org.springframework.web.bind.annotation.*;

/**
 * 短信网关配置管理（仅管理员）：查看/修改 provider 及阿里云/腾讯云/华为云凭证，保存即热生效。
 * 凭证仅在内网 Redis 持久化，不落日志。
 */
@RestController
@RequestMapping("/api/auth/sms/admin")
public class SmsAdminController {

    private final SmsConfigService configService;

    public SmsAdminController(SmsConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/config")
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<SmsConfig> get() {
        return Result.success(configService.get());
    }

    @PutMapping("/config")
    @RequireRole
    @RateLimit(limit = 20, window = 60)
    @OperationLog("修改短信网关配置")
    public Result<Void> save(@RequestBody SmsConfig config) {
        configService.save(config);
        return Result.success();
    }
}
