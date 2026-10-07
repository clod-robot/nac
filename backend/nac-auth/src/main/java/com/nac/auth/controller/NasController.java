package com.nac.auth.controller;

import com.nac.auth.entity.NasDevice;
import com.nac.auth.mapper.NasMapper;
import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.exception.BusinessException;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.Result;
import com.nac.common.result.ResultCode;
import com.nac.common.security.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NAS 管理（仅管理员）：
 * 1) 查看/修改 RADIUS 与 NAS 约定的共享密钥（写入 Redis，nac-radius 热生效）；
 * 2) 列出已发起认证的 NAS 设备台账（成功/失败次数、最近认证时间等）。
 */
@RestController
@RequestMapping("/api/auth/nas")
public class NasController {

    private final NasMapper nasMapper;
    private final RedisUtil redisUtil;

    public NasController(NasMapper nasMapper, RedisUtil redisUtil) {
        this.nasMapper = nasMapper;
        this.redisUtil = redisUtil;
    }

    /** 查询当前共享密钥（未自定义时返回空串 + customized=false，表示使用服务端默认配置） */
    @GetMapping("/secret")
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public Result<Map<String, Object>> secret() {
        String s = redisUtil.get(RedisKeyConstants.RADIUS_SHARED_SECRET);
        Map<String, Object> m = new HashMap<>();
        m.put("secret", s == null ? "" : s);
        m.put("customized", s != null);
        return Result.success(m);
    }

    /** 修改共享密钥：4-64 位，写入 Redis 持久化，nac-radius 即时生效 */
    @PutMapping("/secret")
    @RequireRole
    @RateLimit(limit = 20, window = 60)
    @OperationLog("修改NAS共享密钥")
    public Result<Void> setSecret(@RequestBody Map<String, String> body) {
        String s = body == null ? null : body.get("secret");
        if (s == null || (s = s.trim()).isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密钥不能为空");
        }
        if (s.length() < 4 || s.length() > 64) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密钥长度需 4-64 位");
        }
        redisUtil.set(RedisKeyConstants.RADIUS_SHARED_SECRET, s);
        return Result.success();
    }

    /** 已认证 NAS 设备列表 */
    @GetMapping("/list")
    @RequireRole
    @RateLimit(limit = 120, window = 60)
    public Result<List<NasDevice>> list() {
        return Result.success(nasMapper.list());
    }

    /** 编辑 NAS 名称 */
    @PutMapping("/name")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("修改NAS名称")
    public Result<Void> rename(@RequestBody Map<String, String> body) {
        String ip = body == null ? null : body.get("nasIp");
        String name = body == null ? null : body.get("nasName");
        if (ip == null || ip.trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "NAS IP 不能为空");
        }
        if (name != null && name.length() > 64) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "名称长度不能超过 64");
        }
        nasMapper.updateName(ip.trim(), name == null ? null : name.trim());
        return Result.success();
    }
}
