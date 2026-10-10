package com.nac.radius.controller;

import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import com.nac.radius.netty.PortalV2Server;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Portal 协议运行时状态（管理端）：展示当前实际监听的协议版本与端口。
 */
@RestController
@RequestMapping("/api/radius/portal")
public class PortalStatusController {

    private final PortalV2Server portalServer;

    public PortalStatusController(PortalV2Server portalServer) {
        this.portalServer = portalServer;
    }

    @GetMapping("/status")
    @RequireRole
    @RateLimit(limit = 120, window = 60)
    public Result<Map<String, Object>> status() {
        return Result.success(portalServer.status());
    }
}
