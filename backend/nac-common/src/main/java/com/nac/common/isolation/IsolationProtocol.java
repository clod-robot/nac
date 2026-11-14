package com.nac.common.isolation;

/**
 * 隔离区 / 修复区协议常量（nac-auth 与 nac-radius 共享）。
 *
 * <p>Redis 状态键：
 * <ul>
 *   <li>{@code nac:iso:state:<mac>} 终端当前区域与原因（JSON），供客户端查询；</li>
 *   <li>{@code nac:swa:report:<mac>} 最近一次心跳合规标记（TTL 90s）；</li>
 *   <li>{@code nac:swa:once:<mac>} ONCE 模式下首次合规确认标记（TTL 12h）。</li>
 * </ul>
 */
public final class IsolationProtocol {

    private IsolationProtocol() {}

    public static final String ZONE_QUARANTINE = "QUARANTINE";
    public static final String ZONE_REMEDIATION = "REMEDIATION";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RELEASED = "RELEASED";

    public static final String MODE_ACL = "ACL";
    public static final String MODE_VLAN = "VLAN";

    public static final String STATE_KEY_PREFIX = "nac:iso:state:";
    public static final int STATE_TTL = 86_400;

    /** ONCE 模式合规确认有效期：12 小时（覆盖一个工作日会话）。 */
    public static final int ONCE_TTL = 43_200;

    public static String normalizeMac(String mac) {
        if (mac == null) return "";
        return mac.replaceAll("[^0-9a-fA-F]", "").toLowerCase(java.util.Locale.ROOT);
    }

    public static String stateKey(String mac) {
        return STATE_KEY_PREFIX + normalizeMac(mac);
    }

    /** 归一化区域：非法值回退隔离区。 */
    public static String zone(String raw) {
        return ZONE_REMEDIATION.equalsIgnoreCase(raw == null ? "" : raw.trim())
                ? ZONE_REMEDIATION : ZONE_QUARANTINE;
    }

    /** 隔离/修复状态（写入 Redis，客户端查询）。 */
    public record State(String zone, String status, String reason, String channelType,
                        String channelValue, String remediationUrl) {}
}
