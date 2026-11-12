package com.nac.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nac.auth.entity.OnlineSession;
import com.nac.auth.entity.PortalConfig;
import com.nac.auth.entity.SoftwarePolicy;
import com.nac.auth.mapper.OnlineSessionMapper;
import com.nac.auth.mapper.PortalConfigMapper;
import com.nac.auth.mapper.SoftwarePolicyMapper;
import com.nac.common.exception.BusinessException;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.ResultCode;
import com.nac.common.software.SoftwareBaseline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 终端软件基线策略服务（Portal 侧）：策略 CRUD + 配置热读取 + 心跳合规判定。
 *
 * <p>策略与开关读库结果做 10s 短 TTL 缓存，认证热路径不为每次请求付出一次全表扫描；
 * 客户端心跳（合规状态）由 nac-radius 的 /api/client/software-report 写入 Redis 共享。</p>
 */
@Slf4j
@Service
public class SoftwarePolicyService {

    /** 客户端心跳 Redis 键前缀：由 nac-radius 写入，TTL 见 REPORT_TTL。 */
    public static final String REPORT_KEY_PREFIX = "nac:swa:report:";
    /** 心跳有效期（秒）：客户端 30s 一次，90s 允许两次丢包。 */
    public static final int REPORT_TTL = 90;

    private static final long CACHE_TTL_MS = 10_000;
    private static final String KEY_ENABLED = "softwareBaselineEnabled";
    private static final String KEY_MODE = "softwareBaselineMode";
    private static final String KEY_SECRET = "softwareBaselineSecret";

    private final SoftwarePolicyMapper mapper;
    private final PortalConfigMapper configMapper;
    private final OnlineSessionMapper onlineMapper;
    private final RedisUtil redisUtil;

    private volatile long cfgExpire;
    private volatile boolean enabled;
    private volatile String mode = SoftwareBaseline.MODE_ANY;
    private volatile String secret = "";

    private volatile long polExpire;
    private volatile List<SoftwareBaseline.Policy> policies = List.of();

    public SoftwarePolicyService(SoftwarePolicyMapper mapper, PortalConfigMapper configMapper,
                                 OnlineSessionMapper onlineMapper, RedisUtil redisUtil) {
        this.mapper = mapper;
        this.configMapper = configMapper;
        this.onlineMapper = onlineMapper;
        this.redisUtil = redisUtil;
    }

    // ---------------- 策略 CRUD ----------------

    public List<SoftwarePolicy> list() {
        return mapper.selectAll();
    }

    public void create(SoftwarePolicy p) {
        normalizeAndValidate(p);
        if (p.getEnabled() == null) p.setEnabled(1);
        mapper.insert(p);
        invalidate();
    }

    public void update(SoftwarePolicy p) {
        if (p.getId() == null) throw new BusinessException(400, "缺少 id");
        normalizeAndValidate(p);
        mapper.update(p);
        invalidate();
    }

    public void delete(Long id) {
        mapper.deleteById(id);
        invalidate();
    }

    /** 归一化并校验：进程名必填；HASH 模式必须提供 64 位 SHA256。 */
    private void normalizeAndValidate(SoftwarePolicy p) {
        if (p.getProcessName() == null || p.getProcessName().isBlank())
            throw new BusinessException(400, "进程名不能为空");
        String name = SoftwareBaseline.normName(p.getProcessName());
        if (name.isEmpty() || !name.matches("[0-9a-z0-9._\\-\\u4e00-\\u9fa5]{1,64}"))
            throw new BusinessException(400, "进程名格式不合法");
        p.setProcessName(name);
        if (p.getName() == null || p.getName().isBlank()) p.setName(name);
        p.setName(p.getName().trim());
        if (p.getName().length() > 64) throw new BusinessException(400, "软件名过长");

        String hash = p.getSha256() == null ? "" : p.getSha256().trim().toLowerCase(Locale.ROOT);
        if (!hash.isEmpty() && !hash.matches("[0-9a-f]{64}"))
            throw new BusinessException(400, "SHA256 需为 64 位十六进制");
        p.setSha256(hash.isEmpty() ? null : hash);

        String m = p.getMatchMode() == null ? "" : p.getMatchMode().trim().toUpperCase(Locale.ROOT);
        if (!"HASH".equals(m)) m = "PROCESS";
        if ("HASH".equals(m) && p.getSha256() == null)
            throw new BusinessException(400, "哈希校验模式必须填写 SHA256");
        p.setMatchMode(m);
        if (p.getRequired() == null) p.setRequired(1);
        p.setRequired(p.getRequired() == 0 ? 0 : 1);
        if (p.getEnabled() == null) p.setEnabled(1);
    }

    private void invalidate() {
        cfgExpire = 0;
        polExpire = 0;
    }

    // ---------------- 配置/策略热读取 ----------------

    private void refreshConfig() {
        long now = System.currentTimeMillis();
        if (now < cfgExpire) return;
        boolean e = false;
        String m = SoftwareBaseline.MODE_ANY;
        String s = "";
        try {
            for (PortalConfig c : configMapper.selectAll()) {
                String k = c.getConfigKey();
                if (k == null) continue;
                if (KEY_ENABLED.equals(k)) e = "true".equalsIgnoreCase(c.getConfigValue());
                else if (KEY_MODE.equals(k)) m = c.getConfigValue();
                else if (KEY_SECRET.equals(k)) s = c.getConfigValue() == null ? "" : c.getConfigValue();
            }
        } catch (Exception ex) {
            log.warn("软件基线配置读取失败: {}", ex.getMessage());
        }
        enabled = e;
        mode = m;
        secret = s;
        cfgExpire = now + CACHE_TTL_MS;
    }

    /** 功能开关（读取失败时保持上一次值，默认关闭）。 */
    public boolean isEnabled() {
        refreshConfig();
        return enabled;
    }

    public String mode() {
        refreshConfig();
        return mode;
    }

    public String secret() {
        refreshConfig();
        return secret;
    }

    private void refreshPolicies() {
        long now = System.currentTimeMillis();
        if (now < polExpire) return;
        List<SoftwareBaseline.Policy> out = new ArrayList<>();
        try {
            for (SoftwarePolicy p : mapper.selectEnabled()) {
                out.add(new SoftwareBaseline.Policy(p.getName(), p.getProcessName(), p.getSha256(),
                        p.getMatchMode(), p.getRequired() != null && p.getRequired() == 1));
            }
        } catch (Exception ex) {
            log.warn("软件基线策略读取失败: {}", ex.getMessage());
        }
        policies = List.copyOf(out);
        polExpire = now + CACHE_TTL_MS;
    }

    public List<SoftwareBaseline.Policy> enabledPolicies() {
        refreshPolicies();
        return policies;
    }

    /** 存在启用且标记为必须的策略才真正阻断认证。 */
    public boolean enforced() {
        if (!isEnabled()) return false;
        for (SoftwareBaseline.Policy p : enabledPolicies()) {
            if (p.required()) return true;
        }
        return false;
    }

    // ---------------- 心跳合规判定 ----------------

    /** 终端在有效心跳窗口内且最近一次上报合规。 */
    public boolean isCompliant(String mac) {
        String m = SoftwareBaseline.normMac(mac);
        if (m.isEmpty()) return false;
        return redisUtil.hasKey(REPORT_KEY_PREFIX + m);
    }

    /** Portal 认证前置校验：策略生效且无合规心跳时拒绝。 */
    public void assertCompliant(String mac, String ip) {
        if (!enforced()) return;
        if (isCompliant(mac)) return;
        log.info("Portal 认证拒绝(软件基线): mac={} ip={}", mac, ip);
        throw new BusinessException(ResultCode.SOFTWARE_BASELINE_REQUIRED);
    }

    /** 对外展示：当前判定模式与必需软件名（供管理端排查）。 */
    public List<String> requiredNames() {
        List<String> names = new ArrayList<>();
        if (!enforced()) return names;
        for (SoftwareBaseline.Policy p : enabledPolicies()) {
            if (p.required()) names.add(p.name() == null ? p.processName() : p.name());
        }
        return names;
    }

    // ---------------- 在线终端实时合规看板 ----------------

    /** 心跳实时明细键（由 nac-radius 写入）。 */
    private static final String DETAIL_KEY_PREFIX = "nac:swa:detail:";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 在线终端 + 实时合规状态：在线会话来自 sys_online_session，合规明细来自 Redis 心跳。
     * 未上报（含已关闭客户端）视为不合规；开关未启用时不做判定（compliant 恒为 true）。
     */
    public List<Map<String, Object>> realtime() {
        boolean enf = enforced();
        List<OnlineSession> sessions = onlineMapper.listOnline();
        List<Map<String, Object>> rows = new ArrayList<>(sessions.size());
        for (OnlineSession s : sessions) {
            String mac = SoftwareBaseline.normMac(s.getMac());
            boolean reported = false;
            boolean compliant = false;
            Long lastReport = null;
            String hostname = "";
            String clientVersion = "";
            String ip = s.getFramedIp();
            List<String> missing = List.of();
            String detailJson = mac.isEmpty() ? null : redisUtil.get(DETAIL_KEY_PREFIX + mac);
            if (detailJson != null && !detailJson.isEmpty()) {
                try {
                    JsonNode d = MAPPER.readTree(detailJson);
                    reported = true;
                    compliant = d.path("compliant").asBoolean(false);
                    if (d.hasNonNull("ts")) lastReport = d.path("ts").asLong();
                    hostname = d.path("hostname").asText("");
                    clientVersion = d.path("clientVersion").asText("");
                    String dip = d.path("ip").asText("");
                    if (!dip.isEmpty()) ip = dip;
                    List<String> miss = new ArrayList<>();
                    for (JsonNode n : d.path("missing")) miss.add(n.asText());
                    missing = miss;
                } catch (Exception ex) {
                    log.warn("软件基线实时明细解析失败: mac={} {}", s.getMac(), ex.getMessage());
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", s.getUsername() != null ? s.getUsername() : s.getUsernameMask());
            row.put("mac", s.getMac());
            row.put("ip", ip);
            row.put("nasIp", s.getNasIp());
            row.put("reported", reported);
            row.put("compliant", !enf || (reported && compliant));
            row.put("missing", missing);
            row.put("lastReport", lastReport);
            row.put("hostname", hostname);
            row.put("clientVersion", clientVersion);
            rows.add(row);
        }
        return rows;
    }
}
