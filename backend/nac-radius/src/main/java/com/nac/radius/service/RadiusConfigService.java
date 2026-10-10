package com.nac.radius.service;

import com.nac.common.entity.SysConfig;
import com.nac.common.exception.BusinessException;
import com.nac.radius.config.RadiusProperties;
import com.nac.radius.mapper.ConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RADIUS 可调参数（热加载）：分片大小持久化到 sys_config，读取带短 TTL 缓存，
 * 管理端修改后无需重启即可在后续 EAP 往返中生效。
 */
@Slf4j
@Service
public class RadiusConfigService {

    /** 单片承载 TLS 字节数：下限避免往返过多，上限避免超出 EAPOL MTU 被交换机丢弃。 */
    public static final int FRAG_MIN = 128;
    public static final int FRAG_MAX = 1400;
    public static final int FRAG_DEFAULT = 1020;
    /** 推荐值说明：1020 兼容最稳，1400 最快（标准以太 MTU 安全上限）。 */
    public static final String FRAG_RECOMMEND = "1020（兼容主流交换机）；追求最快可设 1400；老旧交换机设 240~512";

    private static final String KEY_FRAG = "radius.eap.fragment.size";
    private static final long CACHE_TTL_MS = 3000;

    private final ConfigMapper configMapper;
    private final RadiusProperties props;

    private volatile int cachedFrag = -1;
    private volatile long cacheExpire = 0;

    public RadiusConfigService(ConfigMapper configMapper, RadiusProperties props) {
        this.configMapper = configMapper;
        this.props = props;
    }

    /** 当前分片大小（已钳制到合法区间），带 3s 缓存避免每轮查库。 */
    public int fragmentSize() {
        long now = System.currentTimeMillis();
        if (cachedFrag > 0 && now < cacheExpire) return cachedFrag;
        int v = FRAG_DEFAULT;
        try {
            SysConfig c = configMapper.selectByKey(KEY_FRAG);
            if (c != null && c.getConfigValue() != null && !c.getConfigValue().isBlank()) {
                v = Integer.parseInt(c.getConfigValue().trim());
            }
        } catch (Exception e) {
            log.warn("读取分片配置失败，回退默认: {}", e.getMessage());
        }
        if (v < FRAG_MIN || v > FRAG_MAX) v = props.getEapFragmentSize();
        if (v < FRAG_MIN || v > FRAG_MAX) v = FRAG_DEFAULT;
        cachedFrag = v;
        cacheExpire = now + CACHE_TTL_MS;
        return v;
    }

    /** 修改分片大小（校验区间），落库并立即对后续握手生效。 */
    public void setFragmentSize(int size) {
        if (size < FRAG_MIN || size > FRAG_MAX) {
            throw new BusinessException(400, "分片大小须在 " + FRAG_MIN + " ~ " + FRAG_MAX + " 之间");
        }
        configMapper.upsert(KEY_FRAG, String.valueOf(size));
        cachedFrag = size;
        cacheExpire = System.currentTimeMillis() + CACHE_TTL_MS;
        log.info("EAP 分片大小已更新为 {}（热生效）", size);
    }
}
