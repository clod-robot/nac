package com.nac.radius.service;

import com.nac.common.isolation.IsolationProtocol;
import com.nac.common.redis.RedisUtil;
import com.nac.common.software.SoftwareBaseline;
import com.nac.radius.dto.SoftwareReportReq;
import com.nac.radius.entity.SoftwarePolicy;
import com.nac.radius.mapper.PortalConfigReader;
import com.nac.radius.mapper.SoftwarePolicyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 802.1X 侧终端软件基线服务（NAC-SWA v1）。
 *
 * <p>职责：配置/策略热读取；EAP-Notification 挑战签发与应答校验；认证后心跳上报校验；
 * 合规状态写入 Redis 供 Portal 侧共享判定。</p>
 *
 * <p>无状态设计：挑战存 Redis（TTL 120s，一次性消费），避免绑定到某个 NAS 会话对象，
 * 支持多实例水平扩展与 Netty 多事件环并发。</p>
 */
@Slf4j
@Service
public class SoftwareBaselineService {

    public static final String CHALLENGE_KEY_PREFIX = "nac:swa:chal:";
    public static final String REPORT_KEY_PREFIX = "nac:swa:report:";
    /** ONCE 模式：首次合规确认标记（按 MAC + 策略进程名维度）。 */
    public static final String ONCE_KEY_PREFIX = "nac:swa:once:";
    private static final String REPORT_NONCE_PREFIX = "nac:swa:rnonce:";
    private static final int CHALLENGE_TTL = 120;
    private static final int REPORT_TTL = 90;
    private static final int REPORT_NONCE_TTL = 300;
    private static final long TS_WINDOW_SEC = 300;
    private static final long CACHE_TTL_MS = 10_000;

    private final SoftwarePolicyMapper policyMapper;
    private final PortalConfigReader configReader;
    private final RedisUtil redis;
    private final IsolationService isolationService;
    private static final SecureRandom RANDOM = new SecureRandom();

    private volatile long cfgExpire;
    private volatile boolean enabled;
    private volatile String mode = SoftwareBaseline.MODE_ANY;
    private volatile String secret = "";

    private volatile long polExpire;
    private volatile List<SoftwareBaseline.Policy> policies = List.of();
    /** 原始策略（保留弹窗覆盖字段），与 policies 同周期刷新。 */
    private volatile List<SoftwarePolicy> rawPolicies = List.of();

    public SoftwareBaselineService(SoftwarePolicyMapper policyMapper, PortalConfigReader configReader,
                                   RedisUtil redis, IsolationService isolationService) {
        this.policyMapper = policyMapper;
        this.configReader = configReader;
        this.redis = redis;
        this.isolationService = isolationService;
    }

    public record Challenge(String mac, String username, long ts) {}

    public record Result(boolean ok, String message, List<String> missing, int reported) {}

    // ---------------- 配置 / 策略 ----------------

    private void refreshConfig() {
        long now = System.currentTimeMillis();
        if (now < cfgExpire) return;
        boolean e = false;
        String m = SoftwareBaseline.MODE_ANY;
        String s = "";
        try {
            e = "true".equalsIgnoreCase(cfg("softwareBaselineEnabled"));
            String mv = cfg("softwareBaselineMode");
            if (mv != null && !mv.isBlank()) m = mv.trim().toUpperCase(Locale.ROOT);
            String sv = cfg("softwareBaselineSecret");
            if (sv != null) s = sv.trim();
        } catch (Exception ex) {
            log.warn("软件基线配置读取失败: {}", ex.getMessage());
        }
        enabled = e;
        mode = m;
        secret = s;
        cfgExpire = now + CACHE_TTL_MS;
    }

    private String cfg(String key) {
        try {
            return configReader.selectValue(key);
        } catch (Exception e) {
            return null;
        }
    }

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
        List<SoftwarePolicy> raw = new ArrayList<>();
        try {
            for (SoftwarePolicy p : policyMapper.selectEnabled()) {
                raw.add(p);
                out.add(new SoftwareBaseline.Policy(p.getName(), p.getProcessName(), p.getSha256(),
                        p.getMatchMode(), p.getRequired() != null && p.getRequired() == 1, p.getCheckMode()));
            }
        } catch (Exception ex) {
            log.warn("软件基线策略读取失败: {}", ex.getMessage());
        }
        policies = List.copyOf(out);
        rawPolicies = List.copyOf(raw);
        polExpire = now + CACHE_TTL_MS;
    }

    public List<SoftwareBaseline.Policy> policies() {
        refreshPolicies();
        return policies;
    }

    /** 原始策略（含弹窗覆盖字段），供客户端策略接口组装弹窗。 */
    public List<SoftwarePolicy> rawPolicies() {
        refreshPolicies();
        return rawPolicies;
    }

    /** 聚合校验模式：全部必需策略为 ONCE 时返回 ONCE，否则 REALTIME。 */
    public String effectiveCheckMode() {
        boolean has = false;
        for (SoftwareBaseline.Policy p : policies()) {
            if (!p.required()) continue;
            has = true;
            if (!p.once()) return SoftwareBaseline.CHECK_REALTIME;
        }
        return has ? SoftwareBaseline.CHECK_ONCE : SoftwareBaseline.CHECK_REALTIME;
    }

    /** 是否真正阻断：开关打开 + 共享密钥非空 + 存在必需策略（缺密钥无法验签，视为未启用避免误锁死）。 */
    public boolean enforced() {
        if (!isEnabled()) return false;
        if (secret == null || secret.isBlank()) {
            log.warn("软件基线已开启但未配置共享密钥，跳过校验（避免全员断网）");
            return false;
        }
        for (SoftwareBaseline.Policy p : policies()) {
            if (p.required()) return true;
        }
        return false;
    }

    /** 挑战下发的软件名（必需策略的进程名，去重，最多 MAX_NAMES 个）。 */
    public List<String> challengeNames() {
        List<String> names = new ArrayList<>();
        for (SoftwareBaseline.Policy p : policies()) {
            if (!p.required()) continue;
            String n = SoftwareBaseline.normName(p.processName());
            if (n.isEmpty() || names.contains(n)) continue;
            names.add(n);
            if (names.size() >= SoftwareBaseline.MAX_NAMES) break;
        }
        return names;
    }

    // ---------------- EAP 挑战 ----------------

    /** 签发挑战，返回 nonce（32 位 hex）。 */
    public String createChallenge(String mac, String username) {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        String nonce = HexFormat.of().formatHex(raw);
        String nMac = SoftwareBaseline.normMac(mac);
        String value = nMac + "|" + (username == null ? "" : username) + "|" + System.currentTimeMillis();
        redis.set(CHALLENGE_KEY_PREFIX + nonce, value, CHALLENGE_TTL);
        return nonce;
    }

    /** 读取挑战（不删除）；不存在返回 null。 */
    public Challenge getChallenge(String nonce) {
        if (nonce == null || nonce.length() < 16) return null;
        String v = redis.get(CHALLENGE_KEY_PREFIX + nonce);
        if (v == null) return null;
        String[] p = v.split("\\|", 3);
        long ts;
        try {
            ts = p.length >= 3 ? Long.parseLong(p[2]) : 0L;
        } catch (NumberFormatException e) {
            ts = 0L;
        }
        return new Challenge(p[0], p.length >= 2 ? p[1] : "", ts);
    }

    public void consumeChallenge(String nonce) {
        if (nonce != null && !nonce.isBlank()) redis.delete(CHALLENGE_KEY_PREFIX + nonce);
    }

    /**
     * 校验 EAP-Response/Notification 中的 NAC-SWA:2 应答（签名随应答文本 '|' 后缀回传）。
     *
     * @param nonce   挑战 nonce（来自 State 属性）
     * @param mac     认证请求方 MAC（Calling-Station-Id）
     * @param typeData EAP typeData 原文（含 NAC-SWA 前缀与签名后缀）
     */
    public Result verifyEapResponse(String nonce, String mac, String typeData) {
        if (!enforced()) return new Result(true, "未启用软件基线", List.of(), 0);
        SoftwareBaseline.ParsedResponse resp = SoftwareBaseline.parseResponse(typeData);
        if (resp == null) return new Result(false, "软件基线应答格式非法", List.of(), 0);
        Challenge ch = getChallenge(nonce);
        if (ch == null) return new Result(false, "软件基线挑战已过期", List.of(), 0);

        String nMac = SoftwareBaseline.normMac(mac);
        if (!ch.mac().isEmpty() && !ch.mac().equals(nMac))
            return new Result(false, "终端 MAC 与挑战不一致", List.of(), resp.items().size());
        if (!resp.mac().isEmpty() && !resp.mac().equals(nMac))
            return new Result(false, "应答 MAC 与请求不一致", List.of(), resp.items().size());

        String canonical = SoftwareBaseline.canonical(resp.items(), SoftwareBaseline.EAP_HASH_LEN);
        String payload = nonce + "|" + nMac + "|" + canonical;
        if (!SoftwareBaseline.verifySign(secret, payload, resp.signature()))
            return new Result(false, "软件基线签名校验失败", List.of(), resp.items().size());

        return evaluateItems(resp.items(), SoftwareBaseline.EAP_HASH_LEN);
    }

    /** 按当前策略判定上报软件（hashLen 仅影响日志/统计，判定使用前缀比较）。 */
    public Result evaluateItems(List<SoftwareBaseline.Item> items, int hashLen) {
        SoftwareBaseline.EvalResult r = SoftwareBaseline.evaluate(policies(), items, mode, hashLen);
        return new Result(r.ok(), r.reason(), r.missing(), items == null ? 0 : items.size());
    }

    // ---------------- 心跳上报 ----------------

    /** 校验并处理客户端心跳上报；合规则写入 Redis 合规标记（TTL 90s）。 */
    public Result handleReport(SoftwareReportReq req) {
        if (req == null) return new Result(false, "请求体为空", List.of(), 0);
        String mac = SoftwareBaseline.normMac(req.getMac());
        if (mac.length() != 12) return new Result(false, "MAC 格式不合法", List.of(), 0);
        if (!enforced()) return new Result(true, "未启用软件基线", List.of(), 0);

        String nonce = req.getNonce() == null ? "" : req.getNonce().trim();
        if (nonce.length() < 16) return new Result(false, "nonce 缺失", List.of(), 0);
        Long ts = req.getTs();
        if (ts == null || Math.abs(System.currentTimeMillis() / 1000 - ts) > TS_WINDOW_SEC)
            return new Result(false, "时间戳超出允许窗口", List.of(), 0);
        // 防重放：同一 nonce 时间窗内只能用一次（Redis 故障时降级放行）
        if (!redis.setIfAbsent(REPORT_NONCE_PREFIX + nonce, REPORT_NONCE_TTL))
            return new Result(false, "重复上报", List.of(), 0);

        List<SoftwareBaseline.Item> items = new ArrayList<>();
        if (req.getSoftware() != null) {
            for (SoftwareReportReq.SoftwareItem it : req.getSoftware()) {
                if (it == null) continue;
                items.add(new SoftwareBaseline.Item(it.getName(), it.getSha256()));
            }
        }
        String canonical = SoftwareBaseline.canonical(items, 64);
        String payload = nonce + "|" + mac + "|" + ts + "|" + canonical;
        if (!SoftwareBaseline.verifySign(secret, payload, req.getSignature())) {
            clearReport(mac);
            return new Result(false, "签名校验失败", List.of(), items.size());
        }

        List<SoftwareBaseline.Policy> required = requiredPolicies();

        // ONCE：全部必需项均已确认过 → 直接放行（不再采集判定，仅续期合规标记）
        if (!required.isEmpty() && allOnce(required) && onceCount(mac, required) == required.size()) {
            redis.set(REPORT_KEY_PREFIX + mac, "1", REPORT_TTL);
            isolationService.maybeAutoRelease(mac, "软件基线合规（ONCE 已确认）");
            return new Result(true, "ONCE 模式已确认合规", List.of(), items.size());
        }

        SoftwareBaseline.EvalResult er = evaluateConsideringOnce(mac, required, items);
        if (er.ok()) {
            redis.set(REPORT_KEY_PREFIX + mac, "1", REPORT_TTL);
            markOnce(mac, required, items);
            isolationService.maybeAutoRelease(mac, "软件基线合规");
            log.debug("软件基线心跳合规: mac={} host={} 上报={} 项", mac, req.getHostname(), items.size());
        } else {
            clearReport(mac);
            log.info("软件基线心跳不合规: mac={} host={} 原因={}", mac, req.getHostname(), er.reason());
        }
        return new Result(er.ok(), er.reason(), er.missing(), items.size());
    }

    /** 合规标记（Portal 侧同键共享）；ONCE 模式已确认的终端即使暂无心跳也视为合规。 */
    public boolean isCompliant(String mac) {
        String m = SoftwareBaseline.normMac(mac);
        if (m.isEmpty()) return false;
        if (redis.hasKey(REPORT_KEY_PREFIX + m)) return true;
        List<SoftwareBaseline.Policy> required = requiredPolicies();
        return !required.isEmpty() && allOnce(required) && onceCount(m, required) == required.size();
    }

    // ---------------- ONCE 模式 ----------------

    private List<SoftwareBaseline.Policy> requiredPolicies() {
        List<SoftwareBaseline.Policy> out = new ArrayList<>();
        for (SoftwareBaseline.Policy p : policies()) {
            if (p.required()) out.add(p);
        }
        return out;
    }

    private boolean allOnce(List<SoftwareBaseline.Policy> required) {
        for (SoftwareBaseline.Policy p : required) {
            if (!p.once()) return false;
        }
        return true;
    }

    private String onceKey(String mac, SoftwareBaseline.Policy p) {
        return ONCE_KEY_PREFIX + mac + ":" + SoftwareBaseline.normName(p.processName());
    }

    private int onceCount(String mac, List<SoftwareBaseline.Policy> required) {
        int n = 0;
        for (SoftwareBaseline.Policy p : required) {
            if (redis.hasKey(onceKey(mac, p))) n++;
        }
        return n;
    }

    /** 逐策略判定（ONCE 策略命中历史确认标记即视为满足）。 */
    private SoftwareBaseline.EvalResult evaluateConsideringOnce(String mac,
                                                                List<SoftwareBaseline.Policy> required,
                                                                List<SoftwareBaseline.Item> items) {
        if (required.isEmpty()) return new SoftwareBaseline.EvalResult(true, "无可强制软件项", List.of());
        List<String> missing = new ArrayList<>();
        int matched = 0;
        for (SoftwareBaseline.Policy p : required) {
            boolean sat = SoftwareBaseline.evaluate(List.of(p), items, SoftwareBaseline.MODE_ALL, 64).ok()
                    || (p.once() && redis.hasKey(onceKey(mac, p)));
            if (sat) matched++;
            else missing.add(p.name() == null || p.name().isBlank() ? p.processName() : p.name());
        }
        boolean all = !SoftwareBaseline.MODE_ANY.equalsIgnoreCase(mode);
        boolean ok = all ? missing.isEmpty() : matched > 0;
        String reason = ok ? (all ? "软件基线校验通过（全部必需项）" : "软件基线校验通过（任一必需项）")
                : "缺少必需软件: " + String.join(",", missing);
        return new SoftwareBaseline.EvalResult(ok, reason, missing);
    }

    /** 首次合规心跳：为当前满足的 ONCE 策略写入确认标记（TTL 12h）。 */
    private void markOnce(String mac, List<SoftwareBaseline.Policy> required, List<SoftwareBaseline.Item> items) {
        for (SoftwareBaseline.Policy p : required) {
            if (!p.once()) continue;
            if (!SoftwareBaseline.evaluate(List.of(p), items, SoftwareBaseline.MODE_ALL, 64).ok()) continue;
            redis.set(onceKey(mac, p), "1", IsolationProtocol.ONCE_TTL);
        }
    }

    private void clearReport(String mac) {
        redis.delete(REPORT_KEY_PREFIX + mac);
    }
}
