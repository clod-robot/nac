package com.nac.auth.controller;

import com.nac.auth.dto.RadiusStatusVO;
import com.nac.auth.dto.SetRadiusPasswordRequest;
import com.nac.auth.entity.SysUser;
import com.nac.auth.mapper.SysUserMapper;
import com.nac.common.exception.BusinessException;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.result.ResultCode;
import com.nac.common.security.AesCryptoUtil;
import com.nac.common.security.RequireRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * RADIUS 账号开通管理：管理员为指定账号设置 802.1X 专用口令。
 * 口令经 AES-256-GCM 加密后存储（与手机号同源密钥，环境变量注入）。
 * 鉴权与防越权由网关 + @RequireRole 保证。
 */
@RestController
@RequestMapping("/api/auth")
public class RadiusUserController {

    private final SysUserMapper userMapper;
    private final AesCryptoUtil aesCryptoUtil;

    public RadiusUserController(SysUserMapper userMapper, AesCryptoUtil aesCryptoUtil) {
        this.userMapper = userMapper;
        this.aesCryptoUtil = aesCryptoUtil;
    }

    @PostMapping("/radius/password")
    @RequireRole // 仅 admin 白名单可操作
    @RateLimit(limit = 30, window = 60)
    @OperationLog("设置RADIUS口令")
    public Result<Void> setPassword(@Valid @RequestBody SetRadiusPasswordRequest req) {
        SysUser user = userMapper.selectByUsername(req.getUsername());
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 明文不落库、不日志；加密后存储可逆密文供 CHAP/PAP 校验
        String cipher = aesCryptoUtil.encrypt(req.getRadiusPassword());
        int rows = userMapper.updateRadiusCipher(user.getId(), cipher);
        if (rows != 1) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "设置失败");
        }
        return Result.success();
    }

    /** 查询 RADIUS 开通状态（仅返回是否已设口令，不返回密文/明文） */
    @GetMapping("/radius/password")
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<RadiusStatusVO> status(@RequestParam String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名不能为空");
        }
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return Result.success(new RadiusStatusVO(user.getUsername(),
                user.getRadiusPasswordCipher() != null, user.getStatus()));
    }

    /** 清空 RADIUS 口令（关闭该账号 802.1X 准入） */
    @DeleteMapping("/radius/password")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("清空RADIUS口令")
    public Result<Void> clear(@RequestParam String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名不能为空");
        }
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        userMapper.clearRadiusCipher(user.getId());
        return Result.success();
    }
}
