package com.nac.common.coa;

import com.nac.common.redis.RedisUtil;

/**
 * CoA 命令队列的编码/投递工具（nac-auth 与 nac-radius 共用）。
 *
 * <p>使用竖线分隔的定长字段而非 JSON：命令字段少且固定，解析无额外依赖、开销最低；
 * 字段内的 {@code |} 与换行在入队前被替换，保证格式不会被破坏。</p>
 */
public final class CoaQueue {

    private CoaQueue() {}

    private static final int FIELD_COUNT = 7;

    public static void enqueue(RedisUtil redis, CoaProtocol.Command c) {
        if (c == null || c.mac() == null || c.mac().isEmpty()) return;
        redis.lpushCapped(CoaProtocol.QUEUE_KEY, encode(c), CoaProtocol.QUEUE_MAX);
    }

    public static String encode(CoaProtocol.Command c) {
        return String.join("|", clean(c.mac()), clean(c.action()), clean(c.zone()),
                clean(c.channelType()), clean(c.channelValue()), clean(c.reason()),
                String.valueOf(c.ts()));
    }

    /** 解析失败返回 null（脏数据直接丢弃，不影响消费线程）。 */
    public static CoaProtocol.Command decode(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String[] p = raw.split("\\|", FIELD_COUNT);
        if (p.length < FIELD_COUNT) return null;
        long ts;
        try {
            ts = Long.parseLong(p[6].trim());
        } catch (NumberFormatException e) {
            ts = 0L;
        }
        return new CoaProtocol.Command(p[0], p[1], p[2], p[3], p[4], p[5], ts);
    }

    private static String clean(String s) {
        if (s == null) return "";
        String v = s.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
        return v.length() > 120 ? v.substring(0, 120) : v;
    }
}
