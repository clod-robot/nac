package com.nac.log.controller;

import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import com.nac.log.entity.AuthLog;
import com.nac.log.entity.OnlineSession;
import com.nac.log.entity.SysLog;
import com.nac.log.mapper.AuthLogMapper;
import com.nac.log.mapper.OnlineSessionMapper;
import com.nac.log.mapper.SysLogMapper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志查询（仅管理员）：操作日志、认证日志、在线会话。删除接口不对外。
 */
@RestController
@RequestMapping("/api/log")
@RequireRole("admin")
public class LogController {

    private final SysLogMapper sysLogMapper;
    private final AuthLogMapper authLogMapper;
    private final OnlineSessionMapper onlineSessionMapper;
    private final RedisUtil redisUtil;

    public LogController(SysLogMapper sysLogMapper, AuthLogMapper authLogMapper,
                         OnlineSessionMapper onlineSessionMapper, RedisUtil redisUtil) {
        this.sysLogMapper = sysLogMapper;
        this.authLogMapper = authLogMapper;
        this.onlineSessionMapper = onlineSessionMapper;
        this.redisUtil = redisUtil;
    }

    @GetMapping("/list")
    @RequireRole("admin")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        int offset = (page - 1) * size;
        List<SysLog> list = sysLogMapper.selectPage(offset, size);
        long total = countWithCache();
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.success(data);
    }

    /** 认证日志（login/portal/radius），支持按类型、结果筛选 */
    @GetMapping("/auth-list")
    @RequireRole("admin")
    public Result<Map<String, Object>> authList(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) Integer result) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        int offset = (page - 1) * size;
        List<AuthLog> list = authLogMapper.selectPage(offset, size, type, result);
        long total = authLogMapper.countAll(type, result);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.success(data);
    }

    /** 在线会话（RADIUS 计费驱动的在线终端） */
    @GetMapping("/online-list")
    @RequireRole("admin")
    public Result<Map<String, Object>> onlineList(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        int offset = (page - 1) * size;
        List<OnlineSession> list = onlineSessionMapper.selectPage(offset, size);
        long total = onlineSessionMapper.countAll();
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.success(data);
    }

    /** 强制下线（删除在线会话记录） */
    @DeleteMapping("/online/{id}")
    @RequireRole("admin")
    public Result<Void> offline(@PathVariable("id") Long id) {
        onlineSessionMapper.deleteById(id);
        return Result.success();
    }

    private long countWithCache() {
        try {
            String cached = redisUtil.get(RedisKeyConstants.LOG_COUNT_ALL);
            if (cached != null) return Long.parseLong(cached);
            long total = sysLogMapper.countAll();
            redisUtil.set(RedisKeyConstants.LOG_COUNT_ALL, String.valueOf(total), 60);
            return total;
        } catch (Exception e) {
            return sysLogMapper.countAll();
        }
    }
}
