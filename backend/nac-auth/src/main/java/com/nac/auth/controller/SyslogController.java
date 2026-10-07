package com.nac.auth.controller;

import com.nac.auth.service.SyslogConfigService;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import com.nac.common.syslog.SyslogConfig;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 日志管理 - syslog 外发配置（仅管理员）：
 *  - GET  /config：读取当前外发配置
 *  - PUT  /config：保存配置（启用/服务器/端口/协议/facility/分类开关），保存即热生效
 *  - POST /test：用当前配置发送一条测试报文，验证连通性
 */
@RestController
@RequestMapping("/api/auth/syslog")
public class SyslogController {

    private final SyslogConfigService configService;

    public SyslogController(SyslogConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/config")
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<SyslogConfig> get() {
        return Result.success(configService.get());
    }

    @PutMapping("/config")
    @RequireRole
    @RateLimit(limit = 20, window = 60)
    @OperationLog("修改日志外发(syslog)配置")
    public Result<Void> save(@RequestBody SyslogConfig config) {
        configService.save(config);
        return Result.success();
    }

    @PostMapping("/test")
    @RequireRole
    @RateLimit(limit = 20, window = 60)
    public Result<Map<String, Object>> test() {
        boolean ok = configService.test();
        return Result.success(Map.of("success", ok));
    }
}
