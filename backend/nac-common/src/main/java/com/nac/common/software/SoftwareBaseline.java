package com.nac.common.software;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 终端软件基线（NAC-SWA v1）协议与判定纯逻辑。
 *
 * <p>设计目标：服务端 nac-auth（Portal）与 nac-radius（802.1X EAP）共用同一套规范化 /
 * 签名 / 判定算法，避免两处实现漂移；所有方法无状态、可并发调用。</p>
 *
 * <pre>
 * 挑战（服务端 -> 客户端，EAP-Request/Notification typeData）：
 *   NAC-SWA:1:&lt;nonce 32hex&gt;:&lt;软件名1,软件名2,...&gt;
 * 应答（客户端 -> 服务端，EAP-Response/Notification typeData）：
 *   NAC-SWA:2:&lt;mac 12hex&gt;:&lt;count&gt;:&lt;名=sha256前16&gt;;&lt;名=sha256前16&gt;|&lt;signature&gt;
 * 签名：HMAC-SHA256(secret, "nonce|mac|canonicalList") 小写 hex
 * canonicalList：按归一化名升序，条目 "名=哈希"，';' 分隔
 * </pre>
 */
public final class SoftwareBaseline {

    private SoftwareBaseline() {}

    public static final String PROTO = "NAC-SWA";
    /** EAP 通道内哈希截断长度（受 EAPoL MTU 限制） */
    public static final int EAP_HASH_LEN = 16;
    /** 一次挑战下发的软件名上限（防止 EAP 报文超 MTU） */
    public static final int MAX_NAMES = 12;
    public static final String MODE_ANY = "ANY";
    public static final String MODE_ALL = "ALL";
    /** 每次心跳都校验 */
    public static final String CHECK_REALTIME = "REALTIME";
    /** 认证时 + 首次心跳通过后不再校验（ONCE 标记有效期见 ONCE_TTL） */
    public static final String CHECK_ONCE = "ONCE";

    /**
     * 策略条目（对应 sys_software_policy 一行）。
     * checkMode=REALTIME/ONCE；为空按 REALTIME 处理。
     */
    public record Policy(String name, String processName, String sha256, String matchMode,
                         boolean required, String checkMode) {

        public boolean once() {
            return CHECK_ONCE.equalsIgnoreCase(checkMode == null ? "" : checkMode.trim());
        }
    }

    /** 客户端上报的一件软件：归一化名 + SHA256（可为截断） */
    public record Item(String name, String sha256) {}

    public record EvalResult(boolean ok, String reason, List<String> missing) {}

    public record ParsedChallenge(String nonce, List<String> names) {}

    /** 应答解析结果：items 为上报软件，signature 为客户端 HMAC（可为 null）。 */
    public record ParsedResponse(String mac, List<Item> items, String signature) {}

    /** 归一化进程名：去路径、去 .exe/.com 扩展名、小写（Windows 进程名大小写不敏感）。 */
    public static String normName(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int slash = Math.max(s.lastIndexOf('\\'), s.lastIndexOf('/'));
        if (slash >= 0 && slash < s.length() - 1) s = s.substring(slash + 1);
        s = s.toLowerCase(Locale.ROOT);
        if (s.endsWith(".exe")) s = s.substring(0, s.length() - 4);
        else if (s.endsWith(".com")) s = s.substring(0, s.length() - 4);
        return s;
    }

    /** 归一化 MAC：仅保留十六进制字符并小写（兼容 aa:bb / aa-bb / aabb）。 */
    public static String normMac(String mac) {
        if (mac == null) return "";
        return mac.replaceAll("[^0-9a-fA-F]", "").toLowerCase(Locale.ROOT);
    }

    /** 规范化报文串：按归一化名升序，去重（同名保留首个），'名=哈希' 以 ';' 连接。 */
    public static String canonical(List<Item> items, int hashLen) {
        if (items == null || items.isEmpty()) return "";
        Map<String, String> uniq = new LinkedHashMap<>();
        for (Item it : items) {
            if (it == null) continue;
            String n = normName(it.name());
            if (n.isEmpty()) continue;
            String h = trimHash(it.sha256(), hashLen);
            if (h.isEmpty()) continue;
            uniq.putIfAbsent(n, h);
        }
        List<Map.Entry<String, String>> list = new ArrayList<>(uniq.entrySet());
        list.sort(Comparator.comparing(Map.Entry::getKey));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : list) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /** HMAC-SHA256 小写 hex。 */
    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    /** 恒定时间验签。 */
    public static boolean verifySign(String secret, String payload, String sign) {
        if (secret == null || secret.isBlank() || sign == null) return false;
        String expect = sign(secret, payload);
        return constantTimeEquals(expect, sign.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** 构造挑战文本：NAC-SWA:1:nonce:name1,name2（名最多 MAX_NAMES 个）。 */
    public static String buildChallenge(String nonce, List<String> names) {
        StringBuilder sb = new StringBuilder(PROTO).append(':').append(1).append(':').append(nonce).append(':');
        int n = 0;
        if (names != null) {
            for (String s : names) {
                String v = normName(s);
                if (v.isEmpty()) continue;
                if (n++ > 0) sb.append(',');
                sb.append(v);
                if (n >= MAX_NAMES) break;
            }
        }
        return sb.toString();
    }

    /** 解析挑战文本，非法返回 null。 */
    public static ParsedChallenge parseChallenge(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (!s.startsWith(PROTO + ":")) return null;
        String[] p = s.split(":", 4);
        if (p.length < 3 || !"1".equals(p[1])) return null;
        String nonce = p[2] == null ? "" : p[2].trim();
        if (nonce.length() < 16) return null;
        List<String> names = new ArrayList<>();
        if (p.length == 4 && !p[3].isBlank()) {
            for (String x : p[3].split(",")) {
                String v = normName(x);
                if (!v.isEmpty()) names.add(v);
            }
        }
        return new ParsedChallenge(nonce, names);
    }

    /** 构造应答文本：NAC-SWA:2:mac:count:name=hash16;...|signature。 */
    public static String buildResponse(String mac, List<Item> items, String signature) {
        String m = normMac(mac);
        String canon = canonical(items, EAP_HASH_LEN);
        int count = canon.isEmpty() ? 0 : canon.split(";").length;
        return PROTO + ":2:" + m + ":" + count + ":" + canon + "|" + (signature == null ? "" : signature);
    }

    /** 解析应答文本（兼容哈希全长度上报），非法返回 null。 */
    public static ParsedResponse parseResponse(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (!s.startsWith(PROTO + ":")) return null;
        String sig = null;
        int bar = s.indexOf('|');
        if (bar >= 0) {
            sig = s.substring(bar + 1).trim().toLowerCase(Locale.ROOT);
            s = s.substring(0, bar);
        }
        String[] p = s.split(":", 5);
        if (p.length < 5 || !"2".equals(p[1])) return null;
        List<Item> items = new ArrayList<>();
        if (!p[4].isBlank()) {
            for (String kv : p[4].split(";")) {
                int eq = kv.indexOf('=');
                if (eq <= 0 || eq == kv.length() - 1) continue;
                String n = normName(kv.substring(0, eq));
                String h = kv.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
                if (n.isEmpty() || !h.matches("[0-9a-f]{8,64}")) continue;
                items.add(new Item(n, h));
            }
        }
        return new ParsedResponse(normMac(p[2]), items, sig);
    }

    /**
     * 策略判定。
     *
     * @param policies 已启用的策略
     * @param items    客户端上报软件（名已归一化，哈希可为截断）
     * @param mode     ANY=任一必需项满足 / ALL=全部必需项满足（缺省按 ANY）
     * @param hashLen  上报哈希期望长度（判定只比较两者较短长度的前缀）
     */
    public static EvalResult evaluate(List<Policy> policies, List<Item> items, String mode, int hashLen) {
        if (policies == null || policies.isEmpty())
            return new EvalResult(true, "无软件基线策略", List.of());
        List<Policy> required = new ArrayList<>();
        for (Policy p : policies) if (p != null && p.required()) required.add(p);
        if (required.isEmpty()) return new EvalResult(true, "无可强制软件项", List.of());

        Map<String, String> reports = new LinkedHashMap<>();
        if (items != null) {
            for (Item it : items) {
                if (it == null) continue;
                String n = normName(it.name());
                if (n.isEmpty()) continue;
                String h = it.sha256() == null ? "" : it.sha256().trim().toLowerCase(Locale.ROOT);
                // 同名多条时优先保留哈希较长的一条（信息更完整）
                String prev = reports.get(n);
                if (prev == null || h.length() > prev.length()) reports.put(n, h);
            }
        }

        List<String> missing = new ArrayList<>();
        int matched = 0;
        for (Policy p : required) {
            if (satisfied(p, reports)) matched++;
            else missing.add(p.name() == null || p.name().isBlank() ? p.processName() : p.name());
        }
        boolean all = !MODE_ANY.equalsIgnoreCase(mode == null ? "" : mode.trim());
        boolean ok = all ? missing.isEmpty() : matched > 0;
        String reason = ok
                ? (all ? "软件基线校验通过（全部必需项）" : "软件基线校验通过（任一必需项）")
                : "缺少必需软件: " + String.join(",", missing);
        return new EvalResult(ok, reason, missing);
    }

    private static boolean satisfied(Policy p, Map<String, String> reports) {
        String name = normName(p.processName());
        if (name.isEmpty()) return false;
        String got = reports.get(name);
        if (got == null || got.isEmpty()) return false;
        boolean needHash = "HASH".equalsIgnoreCase(p.matchMode())
                && p.sha256() != null && !p.sha256().isBlank();
        if (!needHash) return true;
        return hashPrefixMatches(got, p.sha256());
    }

    /** 哈希前缀比较：取两边较短长度比较（服务端存全量，客户端 EAP 通道只回前 16 位）。 */
    private static boolean hashPrefixMatches(String reported, String policy) {
        String a = reported.trim().toLowerCase(Locale.ROOT);
        String b = policy.trim().toLowerCase(Locale.ROOT);
        int len = Math.min(a.length(), b.length());
        if (len == 0) return false;
        return a.regionMatches(0, b, 0, len);
    }

    private static String trimHash(String sha, int maxLen) {
        if (sha == null) return "";
        String h = sha.trim().toLowerCase(Locale.ROOT);
        if (h.isEmpty() || !h.matches("[0-9a-f]+")) return "";
        if (maxLen > 0 && h.length() > maxLen) return h.substring(0, maxLen);
        return h;
    }
}
