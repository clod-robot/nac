package com.nac.auth.controller;

import com.nac.auth.entity.ExemptTerminal;
import com.nac.auth.service.ExemptTerminalService;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 免认证终端管理（仅 admin）：维护 MAC/IP 白名单，命中即放行认证。 */
@RestController
@RequestMapping("/api/auth/admin/exempt-terminals")
public class ExemptTerminalController {

    private final ExemptTerminalService service;

    public ExemptTerminalController(ExemptTerminalService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<List<ExemptTerminal>> list() {
        return Result.success(service.list());
    }

    @PostMapping
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("新增免认证终端")
    public Result<Void> create(@RequestBody ExemptTerminal t) {
        service.create(t);
        return Result.success();
    }

    @PutMapping
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("修改免认证终端")
    public Result<Void> update(@RequestBody ExemptTerminal t) {
        service.update(t);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("删除免认证终端")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
