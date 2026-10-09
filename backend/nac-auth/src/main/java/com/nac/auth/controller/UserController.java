package com.nac.auth.controller;

import com.nac.auth.entity.SysUser;
import com.nac.auth.mapper.SysUserMapper;
import com.nac.common.context.UserContext;
import com.nac.common.exception.BusinessException;
import com.nac.common.result.Result;
import com.nac.common.security.PhoneCryptoUtil;
import com.nac.common.security.RequireRole;
import com.nac.common.log.OperationLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 后台账号管理（仅 admin）：列表、新增、启用/禁用、终端上限、重置密码、删除。
 * 鉴权由网关校验 token + 方法级 @RequireRole 双重保证。
 */
@RestController
@RequestMapping("/api/auth/admin/users")
public class UserController {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PhoneCryptoUtil phoneCryptoUtil;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaProvider;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OP_TOPIC = "nac-op-log";
    private static final Pattern PHONE_RE = Pattern.compile("^\\d{6,15}$");
    /** 认证方式白名单：仅允许 portal / eap-tls */
    private static final java.util.Set<String> ALLOWED_AUTH_METHODS = new java.util.HashSet<>(java.util.Arrays.asList("portal", "eap-tls"));

    @Value("${nac.crypto.phone-password:NcePhone@2026}")
    private String blindSecret;

    public UserController(SysUserMapper userMapper, PasswordEncoder passwordEncoder, PhoneCryptoUtil phoneCryptoUtil,
                          ObjectProvider<KafkaTemplate<String, String>> kafkaProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.phoneCryptoUtil = phoneCryptoUtil;
        this.kafkaProvider = kafkaProvider;
    }

    /** 手机号盲索引（与短信登录口径一致），用于唯一性校验与检索 */
    private String blindIndex(String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(blindSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(phone.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("盲索引计算失败", e);
        }
    }

    @GetMapping("/list")
    @RequireRole
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String keyword) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        // 关键词是纯数字(手机号)时，换算成盲索引再查，否则明文手机号搜不到
        String phoneBlind = (keyword != null && PHONE_RE.matcher(keyword.trim()).matches()) ? blindIndex(keyword.trim()) : null;
        List<SysUser> list = userMapper.selectPage((page - 1) * size, size, keyword, phoneBlind);
        for (SysUser u : list) {
            if (u.getPhoneCipher() != null && !u.getPhoneCipher().isBlank()) {
                try { u.setPhoneMasked(PhoneCryptoUtil.mask(phoneCryptoUtil.decrypt(u.getPhoneCipher()))); }
                catch (Exception ignore) {}
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", userMapper.countAll(keyword, phoneBlind));
        return Result.success(data);
    }

    @Data
    public static class CreateReq {
        private String username;
        private String password; // 留空则使用默认密码 admin123，首次登录强制修改
        private String realName;
        private String dept;
        private String phone; // 联系电话/手机号
        private String roleCode;
        private Integer terminalLimit;
        private String authMethod; // 认证方式：portal / eap-tls
    }

    @PostMapping
    @RequireRole
    @OperationLog("新增账号")
    public Result<Void> create(@RequestBody CreateReq req) {
        doCreate(req);
        return Result.success();
    }

    /** 单条创建的核心逻辑（单条新增与批量导入共用），校验失败抛 BusinessException */
    private void doCreate(CreateReq req) {
        if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
            throw new BusinessException(400, "账号不能为空");
        }
        // 密码留空 → 默认 admin123，首次登录强制修改（与管理员账号口径一致）
        String rawPwd = (req.getPassword() == null || req.getPassword().isBlank()) ? "admin123" : req.getPassword();
        if (rawPwd.length() < 6) {
            throw new BusinessException(400, "密码至少 6 位");
        }
        if (userMapper.selectByUsername(req.getUsername().trim()) != null) {
            throw new BusinessException(400, "账号已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(req.getUsername().trim());
        u.setPasswordHash(passwordEncoder.encode(rawPwd));
        u.setRealName(req.getRealName());
        u.setDept(req.getDept());
        // 联系电话：校验 + 唯一性（盲索引）+ 加密存储，不存明文
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            String phone = req.getPhone().trim();
            if (!PHONE_RE.matcher(phone).matches()) {
                throw new BusinessException(400, "联系电话格式不正确");
            }
            String blind = blindIndex(phone);
            if (userMapper.selectByPhoneBlind(blind) != null) {
                throw new BusinessException(400, "该联系电话已被使用");
            }
            u.setPhoneCipher(phoneCryptoUtil.encrypt(phone));
            u.setPhoneBlind(blind);
        }
        u.setRoleCode((req.getRoleCode() == null || req.getRoleCode().isBlank()) ? "user" : req.getRoleCode());
        u.setStatus(1);
        u.setTerminalLimit(req.getTerminalLimit() == null ? 5 : req.getTerminalLimit());
        u.setAuthMethod(normalizeAuthMethod(req.getAuthMethod()));
        userMapper.insert(u);
    }

    /** 下载批量导入模板（CSV，带 UTF-8 BOM，Excel 可直接打开） */
    @GetMapping("/template")
    @RequireRole
    public void template(HttpServletResponse resp) throws IOException {
        String csv = "账号,密码,姓名,部门,联系电话,角色,终端数量,认证方式\n"
                + "zhangsan,,张三,研发部,13800138000,user,5,eap-tls\n";
        byte[] bytes = ("﻿" + csv).getBytes(StandardCharsets.UTF_8); // BOM 防 Excel 中文乱码
        resp.setContentType("text/csv; charset=utf-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"user-import-template.csv\"");
        resp.setContentLength(bytes.length);
        resp.getOutputStream().write(bytes);
        resp.getOutputStream().flush();
    }

    /** 批量导入：解析 CSV → 逐行复用 doCreate，返回成功/失败计数与逐行错误 */
    @PostMapping("/import")
    @RequireRole
    @OperationLog("批量导入账号")
    public Result<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件为空");
        }
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1); // 去 BOM
        String[] lines = text.split("\\r?\\n");

        Map<String, Integer> header = null;
        List<String[]> rows = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] cells = parseCsvLine(line);
            if (header == null) { header = indexHeader(cells); continue; }
            rows.add(cells);
        }
        if (header == null) {
            throw new BusinessException(400, "模板无表头，请先下载模板");
        }
        if (rows.size() > 1000) {
            throw new BusinessException(400, "单次最多导入 1000 行");
        }

        int success = 0, fail = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String[] c = rows.get(i);
            CreateReq req = new CreateReq();
            req.setUsername(val(c, header, "账号", "用户名"));
            req.setPassword(val(c, header, "密码"));
            req.setRealName(val(c, header, "姓名", "归属人", "使用人"));
            req.setDept(val(c, header, "部门"));
            req.setPhone(val(c, header, "联系电话", "手机", "电话"));
            String role = val(c, header, "角色");
            req.setRoleCode((role == null || role.isBlank()) ? "user" : role.trim());
            String limit = val(c, header, "终端数量", "终端上限");
            if (limit != null && !limit.isBlank()) {
                try { req.setTerminalLimit(Integer.parseInt(limit.trim())); }
                catch (NumberFormatException ignored) { req.setTerminalLimit(5); }
            }
            req.setAuthMethod(val(c, header, "认证方式", "认证"));
            try {
                doCreate(req);
                success++;
            } catch (BusinessException e) {
                fail++;
                Map<String, Object> er = new LinkedHashMap<>();
                er.put("row", i + 2);
                er.put("username", req.getUsername());
                er.put("message", e.getMessage());
                errors.add(er);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", rows.size());
        data.put("success", success);
        data.put("fail", fail);
        data.put("errors", errors);
        return Result.success(data);
    }

    private String val(String[] cells, Map<String, Integer> header, String... names) {
        for (String n : names) {
            Integer idx = header.get(n);
            if (idx != null && idx < cells.length) {
                String v = cells[idx];
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
        }
        return null;
    }

    /** 表头名 → 列下标（兼容常见别名） */
    private Map<String, Integer> indexHeader(String[] cells) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < cells.length; i++) {
            String h = cells[i] == null ? "" : cells[i].trim();
            if (!h.isEmpty()) m.put(h, i);
        }
        return m;
    }

    /** 极简 CSV 行解析：支持双引号包裹与转义("") */
    private String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuote) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQuote = false;
                } else cur.append(ch);
            } else {
                if (ch == '"') inQuote = true;
                else if (ch == ',') { out.add(cur.toString()); cur.setLength(0); }
                else cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    @Data
    public static class StatusReq {
        private Long id;
        private Integer status; // 1 启用 0 禁用
    }

    @PutMapping("/status")
    @RequireRole
    @OperationLog("修改账号状态")
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
    @OperationLog("修改终端数量上限")
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
    @OperationLog("修改账号归属部门")
    public Result<Void> dept(@RequestBody DeptReq req) {
        userMapper.updateDept(req.getId(), req.getDept());
        return Result.success();
    }

    @Data
    public static class AuthMethodReq {
        private Long id;
        private String authMethod;
    }

    /** 修改认证方式：仅允许 portal / eap-tls，白名单校验防注入非法值 */
    @PutMapping("/auth-method")
    @RequireRole
    @OperationLog("修改账号认证方式")
    public Result<Void> authMethod(@RequestBody AuthMethodReq req) {
        userMapper.updateAuthMethod(req.getId(), normalizeAuthMethod(req.getAuthMethod()));
        return Result.success();
    }

    /** 认证方式归一化：空/非法一律回退 eap-tls，白名单校验 */
    private String normalizeAuthMethod(String m) {
        if (m == null) return "eap-tls";
        String v = m.trim().toLowerCase();
        return ALLOWED_AUTH_METHODS.contains(v) ? v : "eap-tls";
    }

    @Data
    public static class ProfileReq {
        private Long id;
        private String dept;
        private String realName;
        private String phone; // 联系电话，留空表示不修改
    }

    /** 更新账号资料：归属部门 + 使用人(姓名) + 联系电话（含 admin）。 */
    @PutMapping("/profile")
    @RequireRole
    @OperationLog("修改账号资料")
    public Result<Void> profile(@RequestBody ProfileReq req) {
        userMapper.updateProfile(req.getId(), req.getDept(), req.getRealName());
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            String phone = req.getPhone().trim();
            if (!PHONE_RE.matcher(phone).matches()) {
                throw new BusinessException(400, "联系电话格式不正确");
            }
            String blind = blindIndex(phone);
            SysUser exist = userMapper.selectByPhoneBlind(blind);
            if (exist != null && !exist.getId().equals(req.getId())) {
                throw new BusinessException(400, "该联系电话已被使用");
            }
            userMapper.updatePhone(req.getId(), phoneCryptoUtil.encrypt(phone), blind);
        }
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
