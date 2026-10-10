package com.nac.auth.controller;

import com.nac.auth.entity.PortalConfig;
import com.nac.auth.service.PortalService;
import com.nac.common.exception.BusinessException;
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
        /** 认证类型：sms(默认短信) / account(账号密码) */
        private String type;
        private String phone;
        private String code;
        private String username;
        private String password;
        private String mac;
        private String ip;
        private String ts;
        private String sign;
    }

    @PostMapping("/auth")
    @RateLimit(limit = 30, window = 60)
    public Result<String> auth(@RequestBody PortalAuthReq req, HttpServletRequest request) {
        // 防伪推：开启后必须携带合法签名，防止伪造重定向绕过认证
        if (!portalService.verifySign(req.getTs(), req.getMac(), req.getSign())) {
            throw new BusinessException(403, "非法重定向：防伪校验未通过");
        }
        String ip = req.getIp() != null ? req.getIp() : clientIp(request);
        boolean account = "account".equalsIgnoreCase(req.getType());
        if (account) {
            if (req.getUsername() == null || req.getUsername().isBlank() || req.getPassword() == null) {
                throw new BusinessException(400, "账号和密码不能为空");
            }
            return Result.success(portalService.authAccount(req.getUsername(), req.getPassword(), req.getMac(), ip));
        }
        if (req.getPhone() == null || req.getPhone().isBlank() || req.getCode() == null || req.getCode().isBlank()) {
            throw new BusinessException(400, "手机号和验证码不能为空");
        }
        return Result.success(portalService.auth(req.getPhone(), req.getCode(), req.getMac(), ip));
    }

    /** 防伪推验签（Portal 页加载时调用）：返回签名是否合法。 */
    @GetMapping("/verify")
    @RateLimit(limit = 60, window = 60)
    public Result<Boolean> verify(@RequestParam(required = false) String ts,
                                  @RequestParam(required = false) String mac,
                                  @RequestParam(required = false) String sign) {
        return Result.success(portalService.verifySign(ts, mac, sign));
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
