package com.nac.auth.service;

import com.nac.auth.entity.PopupTemplate;
import com.nac.auth.mapper.PopupTemplateMapper;
import com.nac.common.exception.BusinessException;
import com.nac.common.redis.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 终端合规弹窗全局模板服务。
 *
 * <p>配置变更通过 Redis 版本号通知 nac-radius 侧清零本地缓存，保证客户端 30s 内拿到新文案。</p>
 */
@Slf4j
@Service
public class PopupTemplateService {

    /** radius 侧监听该版本号（心跳接口读取）；auth 侧每次保存自增。 */
    public static final String VERSION_KEY = "nac:swa:popup:ver";

    private static final long CACHE_TTL_MS = 10_000;

    private final PopupTemplateMapper mapper;
    private final RedisUtil redis;
    private volatile PopupTemplate cached;
    private volatile long expireAt;

    public PopupTemplateService(PopupTemplateMapper mapper, RedisUtil redis) {
        this.mapper = mapper;
        this.redis = redis;
    }

    public PopupTemplate get() {
        long now = System.currentTimeMillis();
        PopupTemplate t = cached;
        if (t != null && now < expireAt) return t;
        try {
            t = mapper.select();
        } catch (Exception e) {
            log.warn("弹窗模板读取失败: {}", e.getMessage());
        }
        if (t == null) t = defaults();
        cached = t;
        expireAt = now + CACHE_TTL_MS;
        return t;
    }

    public void save(PopupTemplate t) {
        validate(t);
        mapper.update(t);
        cached = null;
        expireAt = 0;
        try {
            redis.increment(VERSION_KEY);
        } catch (Exception e) {
            log.warn("弹窗模板版本通知失败: {}", e.getMessage());
        }
    }

    private void validate(PopupTemplate t) {
        if (t.getTitle() == null || t.getTitle().isBlank()) throw new BusinessException(400, "弹窗标题不能为空");
        if (t.getTitle().trim().length() > 128) throw new BusinessException(400, "弹窗标题过长");
        if (t.getContent() == null || t.getContent().isBlank()) throw new BusinessException(400, "弹窗正文不能为空");
        if (t.getContent().trim().length() > 2000) throw new BusinessException(400, "弹窗正文过长");
        t.setTitle(t.getTitle().trim());
        t.setContent(t.getContent().trim());
        t.setActionText(t.getActionText() == null || t.getActionText().isBlank() ? "立即处理" : t.getActionText().trim());
        t.setActionUrl(t.getActionUrl() == null || t.getActionUrl().isBlank() ? null : t.getActionUrl().trim());
        if (t.getDelaySec() == null || t.getDelaySec() < 0) t.setDelaySec(0);
        if (t.getAllowDelay() == null) t.setAllowDelay(1);
        t.setAllowDelay(t.getAllowDelay() == 0 ? 0 : 1);
        if (t.getEnabled() == null) t.setEnabled(1);
    }

    private PopupTemplate defaults() {
        PopupTemplate t = new PopupTemplate();
        t.setId(1L);
        t.setTitle("终端安全合规提醒");
        t.setContent("检测到您的终端未运行必需的安全软件：{software}。请开启后重试，或点击下方按钮获取安装指引。");
        t.setActionText("立即处理");
        t.setDelaySec(30);
        t.setAllowDelay(1);
        t.setEnabled(1);
        return t;
    }
}
