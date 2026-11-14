package com.nac.common.coa;

/**
 * RADIUS 动态授权（RFC 5176）跨服务协议常量。
 *
 * <p>nac-auth（管理端释放/转修复区）与 nac-radius（心跳不合规、自动释放）都可能需要
 * 让在线会话立即生效新的授权结果，统一投递到 Redis 队列，由 nac-radius 的消费者执行，
 * 避免服务间同步 RPC 与循环依赖。</p>
 */
public final class CoaProtocol {

    private CoaProtocol() {}

    /** 待执行命令队列（LPUSH 入队 / RPOP 出队，FIFO）。 */
    public static final String QUEUE_KEY = "nac:coa:cmd";
    /** 幂等键前缀：同一终端同一动作短时间去重，防止状态抖动导致报文风暴。 */
    public static final String DEDUP_PREFIX = "nac:coa:sent:";
    /** 队列长度上限，超出丢弃最旧命令（防止 radius 长时间不可用时队列无界增长）。 */
    public static final int QUEUE_MAX = 2000;
    /** 幂等窗口（秒）。 */
    public static final int DEDUP_TTL = 30;

    /** 断线重认证：释放/恢复时使用（ACL 无法可靠"撤销"，重认证最稳）。 */
    public static final String ACTION_DISCONNECT = "DISCONNECT";
    /** 授权变更：隔离/修复区启用时下发 Filter-Id / VLAN。 */
    public static final String ACTION_AUTH_CHANGE = "AUTH_CHANGE";

    public static final String MODE_NONE = "NONE";
    public static final String MODE_DISCONNECT = "DISCONNECT";
    public static final String MODE_REQUEST = "REQUEST";

    /**
     * 一条动态授权命令。
     *
     * @param mac          终端 MAC（已归一化为 12 位小写）
     * @param action       {@link #ACTION_DISCONNECT} / {@link #ACTION_AUTH_CHANGE}
     * @param zone         目标区域（QUARANTINE / REMEDIATION / NORMAL）
     * @param channelType  ACL / VLAN（AUTH_CHANGE 时用于组装授权属性）
     * @param channelValue ACL 名或 VLAN ID
     * @param reason       审计原因（写入 Reply-Message）
     * @param ts           入队时间（毫秒）
     */
    public record Command(String mac, String action, String zone, String channelType,
                          String channelValue, String reason, long ts) {}
}
