package com.nac.common.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * KEK 提供者注册表（Factory / 责任链装配）：收集所有 KekProvider，按 priority 降序排序，
 * 对外提供"首个可用材料"的责任链解析。新增 provider 只需注册为 Bean，自动入链。
 */
@Component
public class KekProviderRegistry {

    private final List<KekProvider> ordered;

    public KekProviderRegistry(List<KekProvider> providers) {
        List<KekProvider> list = providers == null ? new ArrayList<>() : new ArrayList<>(providers);
        list.sort(Comparator.comparingInt(KekProvider::priority).reversed());
        this.ordered = List.copyOf(list);
    }

    /** 按优先级返回首个非空 KEK 材料；全部不可用返回 null（由 SecretCipher 降级默认并告警） */
    public String resolveFirstAvailable() {
        for (KekProvider p : ordered) {
            try {
                String k = p.getKekMaterial();
                if (k != null && !k.isBlank()) {
                    return k;
                }
            } catch (Exception e) {
                // 单个 provider 异常不影响链路，继续降级到下一个
            }
        }
        return null;
    }

    public List<KekProvider> providers() {
        return ordered;
    }
}
