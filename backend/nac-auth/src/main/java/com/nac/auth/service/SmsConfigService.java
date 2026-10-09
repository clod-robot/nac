package com.nac.auth.service;

import com.alibaba.fastjson2.JSON;
import com.nac.auth.dto.SmsConfig;
import com.nac.auth.mapper.ConfigMapper;
import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.entity.SysConfig;
import com.nac.common.exception.BusinessException;
import com.nac.common.redis.ConfigCache;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 短信网关配置存取（cache-aside）：DB 为真值，Redis 缓存；读取时与 application.yml 默认值合并。
 * 修改即时生效（先落库再刷缓存，SmsService 每次发送前调用 get()）。
 */
@Slf4j
@Service
public class SmsConfigService {

    private static final Set<String> PROVIDERS = Set.of("mock", "aliyun", "tencent", "huawei", "custom");
    private static final String K = RedisKeyConstants.SMS_CONFIG;

    private final RedisUtil redisUtil;
    private final ConfigMapper configMapper;

    @Value("${nac.sms.provider:mock}") private String defProvider;
    @Value("${nac.sms.aliyun.access-key-id:}") private String defAliAk;
    @Value("${nac.sms.aliyun.access-key-secret:}") private String defAliSk;
    @Value("${nac.sms.aliyun.sign-name:NAC准入}") private String defAliSign;
    @Value("${nac.sms.aliyun.template-code:}") private String defAliTpl;
    @Value("${nac.sms.tencent.secret-id:}") private String defTcId;
    @Value("${nac.sms.tencent.secret-key:}") private String defTcKey;
    @Value("${nac.sms.tencent.region:ap-guangzhou}") private String defTcRegion;
    @Value("${nac.sms.tencent.sms-sdk-app-id:}") private String defTcAppId;
    @Value("${nac.sms.tencent.sign-name:NAC准入}") private String defTcSign;
    @Value("${nac.sms.tencent.template-id:}") private String defTcTpl;
    @Value("${nac.sms.huawei.app-key:}") private String defHwKey;
    @Value("${nac.sms.huawei.app-secret:}") private String defHwSecret;
    @Value("${nac.sms.huawei.sender:}") private String defHwSender;
    @Value("${nac.sms.huawei.template-id:}") private String defHwTpl;
    @Value("${nac.sms.huawei.url:https://smsapi.cn-north-4.myhuaweicloud.com:443/sms/batchSendSms/v1}") private String defHwUrl;

    public SmsConfigService(RedisUtil redisUtil, ConfigMapper configMapper) {
        this.redisUtil = redisUtil;
        this.configMapper = configMapper;
    }

    /** 读取生效配置（缓存优先，未命中查库回填，空值回退 env 默认） */
    public SmsConfig get() {
        SmsConfig c = null;
        String json = ConfigCache.get(redisUtil, K,
                () -> { SysConfig sc = configMapper.selectByKey(K); return sc == null ? null : sc.getConfigValue(); },
                null);
        if (json != null && !json.isBlank()) {
            try { c = JSON.parseObject(json, SmsConfig.class); } catch (Exception e) { log.warn("短信配置解析失败: {}", e.getMessage()); }
        }
        if (c == null) c = new SmsConfig();
        if (c.getProvider() == null || c.getProvider().isBlank()) c.setProvider(defProvider);
        if (c.getAliyun() == null) c.setAliyun(new SmsConfig.Aliyun());
        if (c.getTencent() == null) c.setTencent(new SmsConfig.Tencent());
        if (c.getHuawei() == null) c.setHuawei(new SmsConfig.Huawei());
        if (c.getCustom() == null) c.setCustom(new SmsConfig.Custom());

        SmsConfig.Aliyun a = c.getAliyun();
        a.setAccessKeyId(nz(a.getAccessKeyId(), defAliAk));
        a.setAccessKeySecret(nz(a.getAccessKeySecret(), defAliSk));
        a.setSignName(nz(a.getSignName(), defAliSign));
        a.setTemplateCode(nz(a.getTemplateCode(), defAliTpl));

        SmsConfig.Tencent t = c.getTencent();
        t.setSecretId(nz(t.getSecretId(), defTcId));
        t.setSecretKey(nz(t.getSecretKey(), defTcKey));
        t.setRegion(nz(t.getRegion(), defTcRegion));
        t.setAppId(nz(t.getAppId(), defTcAppId));
        t.setSignName(nz(t.getSignName(), defTcSign));
        t.setTemplateId(nz(t.getTemplateId(), defTcTpl));

        SmsConfig.Huawei h = c.getHuawei();
        h.setAppKey(nz(h.getAppKey(), defHwKey));
        h.setAppSecret(nz(h.getAppSecret(), defHwSecret));
        h.setSender(nz(h.getSender(), defHwSender));
        h.setTemplateId(nz(h.getTemplateId(), defHwTpl));
        h.setUrl(nz(h.getUrl(), defHwUrl));
        return c;
    }

    /** 保存配置（校验 provider 合法）；凭证明文存 Redis，仅限内网、不落日志。 */
    public void save(SmsConfig cfg) {
        if (cfg == null || cfg.getProvider() == null || !PROVIDERS.contains(cfg.getProvider())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "网关类型非法");
        }
        if (cfg.getAliyun() == null) cfg.setAliyun(new SmsConfig.Aliyun());
        if (cfg.getTencent() == null) cfg.setTencent(new SmsConfig.Tencent());
        if (cfg.getHuawei() == null) cfg.setHuawei(new SmsConfig.Huawei());
        if (cfg.getCustom() == null) cfg.setCustom(new SmsConfig.Custom());
        String json = JSON.toJSONString(cfg);
        ConfigCache.put(redisUtil, K, json, () -> configMapper.upsert(K, json));
    }

    private static String nz(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
}
