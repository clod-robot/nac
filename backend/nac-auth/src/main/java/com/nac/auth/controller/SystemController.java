package com.nac.auth.controller;

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

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
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
}
