package com.nac.auth.controller;

import com.nac.auth.entity.SysUser;
import com.nac.auth.mapper.SysUserMapper;
import com.nac.common.context.UserContext;
import com.nac.common.exception.BusinessException;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import lombok.Data;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台账号管理（仅 admin）：列表、新增、启用/禁用、终端上限、重置密码、删除。
 * 鉴权由网关校验 token + 方法级 @RequireRole 双重保证。
 */
@RestController
@RequestMapping("/api/auth/admin/users")
public class UserController {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserController(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/list")
    @RequireRole
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        List<SysUser> list = userMapper.selectPage((page - 1) * size, size);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", userMapper.countAll());
        return Result.success(data);
    }

    @Data
    public static class CreateReq {
        private String username;
        private String password;
        private String realName;
        private String roleCode;
        private Integer terminalLimit;
    }

    @PostMapping
    @RequireRole
    public Result<Void> create(@RequestBody CreateReq req) {
        if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
            throw new BusinessException(400, "账号不能为空");
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new BusinessException(400, "密码至少 6 位");
        }
        if (userMapper.selectByUsername(req.getUsername().trim()) != null) {
            throw new BusinessException(400, "账号已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(req.getUsername().trim());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setRealName(req.getRealName());
        u.setRoleCode((req.getRoleCode() == null || req.getRoleCode().isBlank()) ? "user" : req.getRoleCode());
        u.setStatus(1);
        u.setTerminalLimit(req.getTerminalLimit() == null ? 5 : req.getTerminalLimit());
        userMapper.insert(u);
        return Result.success();
    }

    @Data
    public static class StatusReq {
        private Long id;
        private Integer status; // 1 启用 0 禁用
    }

    @PutMapping("/status")
    @RequireRole
    public Result<Void> status(@RequestBody StatusReq req) {
        Long self = UserContext.getUserId();
        if (req.getId() != null && req.getId().equals(self)) {
            throw new BusinessException(403, "不能禁用当前登录账号");
        }
        if (req.getStatus() != null && req.getStatus() == 0) {
            SysUser u = userMapper.selectById(req.getId());
            if (u != null && "admin".equals(u.getRoleCode()) && userMapper.countAdmin() <= 1) {
                throw new BusinessException(400, "至少保留一个启用的管理员");
            }
        }
        userMapper.updateStatus(req.getId(), req.getStatus() == null ? 1 : req.getStatus());
        return Result.success();
    }

    @Data
    public static class LimitReq {
        private Long id;
        private Integer terminalLimit;
    }

    @PutMapping("/terminal-limit")
    @RequireRole
    public Result<Void> terminalLimit(@RequestBody LimitReq req) {
        if (req.getTerminalLimit() == null || req.getTerminalLimit() < 0) {
            throw new BusinessException(400, "终端数量不合法");
        }
        userMapper.updateTerminalLimit(req.getId(), req.getTerminalLimit());
        return Result.success();
    }

    @Data
    public static class PwdReq {
        private Long id;
        private String password;
    }

    @PutMapping("/password")
    @RequireRole
    public Result<Void> password(@RequestBody PwdReq req) {
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new BusinessException(400, "密码至少 6 位");
        }
        userMapper.updatePassword(req.getId(), passwordEncoder.encode(req.getPassword()));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequireRole
    public Result<Void> delete(@PathVariable Long id) {
        Long self = UserContext.getUserId();
        if (id.equals(self)) {
            throw new BusinessException(403, "不能删除当前登录账号");
        }
        SysUser u = userMapper.selectById(id);
        if (u != null && "admin".equals(u.getRoleCode()) && userMapper.countAdmin() <= 1) {
            throw new BusinessException(400, "至少保留一个管理员");
        }
        userMapper.deleteById(id);
        return Result.success();
    }
}
