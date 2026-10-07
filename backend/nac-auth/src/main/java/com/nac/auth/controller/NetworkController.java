package com.nac.auth.controller;

import com.nac.auth.dto.NetApplyReq;
import com.nac.auth.dto.NetInterface;
import com.nac.auth.service.NetworkService;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 网络管理（仅管理员）：读取本机各网卡状态/地址/网关/DNS，并下发修改。 */
@RestController
@RequestMapping("/api/auth/network")
public class NetworkController {

    private final NetworkService networkService;

    public NetworkController(NetworkService networkService) {
        this.networkService = networkService;
    }

    @GetMapping("/interfaces")
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<List<NetInterface>> interfaces() {
        return Result.success(networkService.listInterfaces());
    }

    @PostMapping("/apply")
    @RequireRole
    @RateLimit(limit = 20, window = 60)
    @OperationLog("修改网络配置")
    public Result<Void> apply(@RequestBody NetApplyReq req) {
        networkService.apply(req);
        return Result.success();
    }
}
