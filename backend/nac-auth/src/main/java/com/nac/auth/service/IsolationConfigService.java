package com.nac.auth.service;

import com.nac.auth.entity.IsolationConfig;
import com.nac.auth.mapper.IsolationConfigMapper;
import com.nac.common.exception.BusinessException;
import com.nac.common.isolation.IsolationProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 隔离区/修复区配置服务：单行配置 + 10s 短 TTL 缓存。
 *
 * <p>配置变更后下一个缓存周期生效；下发通道 ACL / VLAN 二选一。</p>
 */
@Slf4j
@Service
public class IsolationConfigService {

    private static final long CACHE_TTL_MS = 10_000;

    private final IsolationConfigMapper mapper;
    private volatile IsolationConfig cached;
    private volatile long expireAt;

    public IsolationConfigService(IsolationConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** 当前配置（表缺失/读取失败时返回安全的默认值：关闭隔离）。 */
    public IsolationConfig get() {
        long now = System.currentTimeMillis();
        IsolationConfig c = cached;
        if (c != null && now < expireAt) return c;
        try {
            c = mapper.select();
        } catch (Exception e) {
            log.warn("隔离配置读取失败: {}", e.getMessage());
        }
        if (c == null) c = defaults();
        cached = c;
        expireAt = now + CACHE_TTL_MS;
        return c;
    }

    public void save(IsolationConfig c) {
        validate(c);
        mapper.update(c);
        cached = null;
        expireAt = 0;
    }

    private void validate(IsolationConfig c) {
        String mode = c.getMode() == null ? "" : c.getMode().trim().toUpperCase(Locale.ROOT);
        c.setMode(IsolationProtocol.MODE_VLAN.equals(mode) ? IsolationProtocol.MODE_VLAN : IsolationProtocol.MODE_ACL);
        if (c.getEnabled() == null) c.setEnabled(0);
        c.setEnabled(c.getEnabled() == 1 ? 1 : 0);
        if (c.getAutoRelease() == null) c.setAutoRelease(1);
        c.setAutoRelease(c.getAutoRelease() == 0 ? 0 : 1);
        if (c.getReleaseHoldMinutes() == null || c.getReleaseHoldMinutes() < 0) c.setReleaseHoldMinutes(0);
        if (IsolationProtocol.MODE_ACL.equals(c.getMode())) {
            if (isBlank(c.getQuarantineAcl()) || isBlank(c.getRemediationAcl()))
                throw new BusinessException(400, "ACL 模式下隔离区/修复区 ACL 名称不能为空");
        } else {
            if (c.getQuarantineVlan() == null || c.getQuarantineVlan() <= 0
                    || c.getRemediationVlan() == null || c.getRemediationVlan() <= 0)
                throw new BusinessException(400, "VLAN 模式下隔离区/修复区 VLAN 必须为正整数");
            if (c.getQuarantineVlan().equals(c.getRemediationVlan()))
                throw new BusinessException(400, "隔离区与修复区 VLAN 不能相同");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private IsolationConfig defaults() {
        IsolationConfig c = new IsolationConfig();
        c.setId(1L);
        c.setEnabled(0);
        c.setMode(IsolationProtocol.MODE_ACL);
        c.setQuarantineAcl("NAC-QUARANTINE");
        c.setRemediationAcl("NAC-REMEDIATION");
        c.setQuarantineVlan(300);
        c.setRemediationVlan(301);
        c.setAutoRelease(1);
        c.setReleaseHoldMinutes(30);
        return c;
    }
}
