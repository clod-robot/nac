package com.nac.auth.controller;

import com.nac.auth.entity.PortalConfig;
import com.nac.auth.service.PortalService;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Portal 接口：
 * - GET  /api/portal/config        匿名（终端入网前展示）
 * - POST /api/portal/auth          匿名（短信认证，颁发 portalToken）
 * - GET  /api/portal/admin/config  管理员读取配置
 * - PUT  /api/portal/admin/config  管理员更新配置
 */
@RestController
@RequestMapping("/api/portal")
public class PortalController {

    private final PortalService portalService;

    public PortalController(PortalService portalService) {
        this.portalService = portalService;
    }

    @GetMapping("/config")
    @RateLimit(limit = 120, window = 60)
    public Result<Map<String, String>> config() {
        return Result.success(portalService.config());
    }

    @Data
    public static class PortalAuthReq {
        @NotBlank private String phone;
        @NotBlank private String code;
        private String mac;
        private String ip;
    }

    @PostMapping("/auth")
    @RateLimit(limit = 30, window = 60)
    public Result<String> auth(@RequestBody PortalAuthReq req, HttpServletRequest request) {
        String ip = req.getIp() != null ? req.getIp() : clientIp(request);
        return Result.success(portalService.auth(req.getPhone(), req.getCode(), req.getMac(), ip));
    }

    @GetMapping("/admin/config")
    @RequireRole("admin")
    public Result<List<PortalConfig>> adminConfig() {
        return Result.success(portalService.adminConfig());
    }

    @PutMapping("/admin/config")
    @RequireRole("admin")
    @OperationLog("修改门户配置")
    public Result<Void> updateConfig(@RequestBody Map<String, String> kv) {
        portalService.updateConfig(kv);
        return Result.success();
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
