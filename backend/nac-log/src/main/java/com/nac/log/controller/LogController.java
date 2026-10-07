package com.nac.log.controller;

import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import com.nac.log.entity.SysLog;
import com.nac.log.mapper.SysLogMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志查询（仅管理员）。服务端分页，总数 Redis 缓存 60s。删除接口不对外。
 */
@RestController
@RequestMapping("/api/log")
@RequireRole("admin")
public class LogController {

    private final SysLogMapper sysLogMapper;
    private final RedisUtil redisUtil;

    public LogController(SysLogMapper sysLogMapper, RedisUtil redisUtil) {
        this.sysLogMapper = sysLogMapper;
        this.redisUtil = redisUtil;
    }

    @GetMapping("/list")
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
