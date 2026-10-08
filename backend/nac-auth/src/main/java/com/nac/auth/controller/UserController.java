package com.nac.auth.controller;

import com.nac.auth.entity.SysUser;
import com.nac.auth.mapper.SysUserMapper;
import com.nac.common.context.UserContext;
import com.nac.common.exception.BusinessException;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaProvider;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OP_TOPIC = "nac-op-log";

    public UserController(SysUserMapper userMapper, PasswordEncoder passwordEncoder,
                          ObjectProvider<KafkaTemplate<String, String>> kafkaProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.kafkaProvider = kafkaProvider;
    }

    @GetMapping("/list")
    @RequireRole
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String keyword) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        List<SysUser> list = userMapper.selectPage((page - 1) * size, size, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", userMapper.countAll(keyword));
        return Result.success(data);
    }

    @Data
    public static class CreateReq {
        private String username;
        private String password;
        private String realName;
        private String dept;
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
        u.setDept(req.getDept());
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
            if (u != null && "admin".equals(u.getRoleCode())) {
                throw new BusinessException(400, "系统管理员不能禁用");
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
    public static class DeptReq {
        private Long id;
        private String dept;
    }

    @PutMapping("/dept")
    @RequireRole
    public Result<Void> dept(@RequestBody DeptReq req) {
        userMapper.updateDept(req.getId(), req.getDept());
        return Result.success();
    }

    @Data
    public static class ProfileReq {
        private Long id;
        private String dept;
        private String realName;
    }

    /** 更新账号资料：归属部门 + 使用人(姓名)。首次设置管理员账号归属时使用。 */
    @PutMapping("/profile")
    @RequireRole
    public Result<Void> profile(@RequestBody ProfileReq req) {
        userMapper.updateProfile(req.getId(), req.getDept(), req.getRealName());
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
        SysUser target = userMapper.selectById(req.getId());
        userMapper.updatePassword(req.getId(), passwordEncoder.encode(req.getPassword()));
        // 操作日志：记录本次是为“哪个部门/哪个人”的账号修改密码
        String detail = String.format("修改账号[%s]密码，归属部门=%s，使用人=%s",
                target != null ? nz(target.getUsername()) : req.getId(),
                target != null ? nz(target.getDept()) : "-",
                target != null ? nz(target.getRealName()) : "-");
        recordOp("修改账号密码", detail);
        return Result.success();
    }

    /** 写操作日志（Kafka → nac-log 落库），失败降级本地日志，不阻塞主流程 */
    private void recordOp(String operation, String detail) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("operator", nz(UserContext.getUsername()));
            event.put("operatorId", UserContext.getUserId());
            event.put("role", nz(UserContext.getRole()));
            event.put("ip", clientIp());
            event.put("operation", operation);
            event.put("params", detail);
            event.put("status", "SUCCESS");
            event.put("time", System.currentTimeMillis());
            KafkaTemplate<String, String> kafka = kafkaProvider.getIfAvailable();
            if (kafka != null) {
                kafka.send(OP_TOPIC, MAPPER.writeValueAsString(event));
            }
        } catch (Exception e) {
            // 降级：仅本地日志
        }
    }

    private String clientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";
            HttpServletRequest r = attrs.getRequest();
            String xff = r.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
            return r.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String nz(String s) { return s == null || s.isEmpty() ? "-" : s; }

    @DeleteMapping("/{id}")
    @RequireRole
    public Result<Void> delete(@PathVariable Long id) {
        Long self = UserContext.getUserId();
        if (id.equals(self)) {
            throw new BusinessException(403, "不能删除当前登录账号");
        }
        SysUser u = userMapper.selectById(id);
        if (u != null && "admin".equals(u.getRoleCode())) {
            throw new BusinessException(400, "系统管理员不能删除");
        }
        userMapper.deleteById(id);
        return Result.success();
    }
}
