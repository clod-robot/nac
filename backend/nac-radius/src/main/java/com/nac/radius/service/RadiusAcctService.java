package com.nac.radius.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nac.radius.entity.OnlineSession;
import com.nac.radius.entity.RadiusAcctRecord;
import com.nac.radius.mapper.OnlineSessionMapper;
import com.nac.radius.mapper.RadiusAcctRecordMapper;
import com.nac.radius.packet.RadiusCodes;
import com.nac.radius.packet.RadiusPacket;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * RADIUS 计费（RFC 2866）：处理 Accounting-Request，维护在线会话并落审计。
 * 任何异常都不影响回 Accounting-Response（协议要求必须应答）。
 */
@Slf4j
@Service
public class RadiusAcctService {

    private final OnlineSessionMapper sessionMapper;
    private final RadiusAcctRecordMapper acctMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AUDIT_TOPIC = "nac-op-log";
    /** 超过该分钟数未收到计费更新的在线会话，自动判离线（兜底，应对收不到 Stop 的场景）。
     *  注意：本环境交换机几乎不发 Interim-Update，长会话 update_time 不前进，
     *  因此阈值必须足够大（12h），仅用于清理真正被遗弃、且不再重连的幽灵会话，避免误判在线会话。 */
    private static final int STALE_MINUTES = 720;
    /** 同一 MAC 在该秒数内重新上线视为连续在线（重认证），沿用原 start_time，避免时长归零/闪烁。 */
    private static final int REAUTH_GRACE_SECONDS = 300;
    private final ScheduledExecutorService sweep = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "acct-sweep");
        t.setDaemon(true);
        return t;
    });

    public RadiusAcctService(OnlineSessionMapper sessionMapper, RadiusAcctRecordMapper acctMapper,
                             KafkaTemplate<String, String> kafkaTemplate) {
        this.sessionMapper = sessionMapper;
        this.acctMapper = acctMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    void startSweep() {
        sweep.scheduleAtFixedRate(() -> {
            try {
                int n = sessionMapper.markStaleOffline(STALE_MINUTES);
                if (n > 0) log.info("清扫超期未更新在线会话 {} 条", n);
            } catch (Exception e) {
                log.warn("在线会话清扫异常: {}", e.getMessage());
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    @PreDestroy
    void stopSweep() {
        sweep.shutdownNow();
    }

    public RadiusPacket account(RadiusPacket request) {
        Integer statusType = request.getInt(RadiusCodes.ACCT_STATUS_TYPE);
        String sessionId = request.getString(RadiusCodes.ACCT_SESSION_ID);
        String realUsername = request.getString(RadiusCodes.USER_NAME);
        String username = mask(realUsername);
        String mac = request.getString(RadiusCodes.CALLING_STATION_ID);
        // NAS-IP / Framed-IP 为 4 字节二进制属性，必须转点分十进制；getString 会产生乱码
        String nasIp = ip4(request.getAttr(RadiusCodes.NAS_IP_ADDRESS));
        String framedIp = ip4(request.getAttr(RadiusCodes.FRAMED_IP_ADDRESS));

        try {
            // 1) 计费历史明细（等保留痕）
            RadiusAcctRecord rec = new RadiusAcctRecord();
            rec.setAcctSessionId(sessionId);
            rec.setUsernameMask(username);
            rec.setMac(mac);
            rec.setNasIp(nasIp);
            rec.setStatusType(statusType);
            rec.setSessionTime(toLong(request.getInt(RadiusCodes.ACCT_SESSION_TIME)));
            rec.setInputOctets(toLong(request.getInt(RadiusCodes.ACCT_INPUT_OCTETS)));
            rec.setOutputOctets(toLong(request.getInt(RadiusCodes.ACCT_OUTPUT_OCTETS)));
            rec.setTerminateCause(request.getInt(RadiusCodes.ACCT_TERMINATE_CAUSE));
            try { acctMapper.insert(rec); } catch (Exception e) { log.error("计费明细落库失败: {}", e.getMessage()); }

            // 2) 在线会话维护
            if (statusType != null && sessionId != null) {
                if (statusType == RadiusCodes.ACCT_STOP) {
                    try { sessionMapper.markOffline(sessionId); } catch (Exception e) { log.error("会话离线更新失败: {}", e.getMessage()); }
                } else { // Start / Interim
                    // 重认证连续性：同 MAC 短时间内重新上线，沿用原 start_time，避免在线时长归零/闪烁
                    LocalDateTime keepStart = null;
                    try {
                        if (mac != null) {
                            OnlineSession prev = sessionMapper.selectLatestByMac(mac);
                            if (prev != null && prev.getStartTime() != null && prev.getUpdateTime() != null
                                    && Duration.between(prev.getUpdateTime(), LocalDateTime.now()).getSeconds() <= REAUTH_GRACE_SECONDS) {
                                keepStart = prev.getStartTime();
                            }
                            sessionMapper.markOthersOffline(mac, sessionId);
                        }
                    } catch (Exception e) { log.error("同MAC旧会话下线失败: {}", e.getMessage()); }
                    OnlineSession s = new OnlineSession();
                    s.setAcctSessionId(sessionId);
                    s.setUsername(realUsername);
                    s.setUsernameMask(username);
                    s.setMac(mac);
                    s.setNasIp(nasIp);
                    s.setFramedIp(framedIp);
                    s.setStartTime(keepStart);
                    try { sessionMapper.upsert(s); } catch (Exception e) { log.error("会话 upsert 失败: {}", e.getMessage()); }
                }
            }

            // 3) Kafka 审计（异步，失败仅告警）
            publishAudit(request, username, statusType);
        } catch (Exception e) {
            log.error("计费处理异常: {}", e.getMessage());
        }

        // 无论如何回 Accounting-Response
        RadiusPacket resp = new RadiusPacket(RadiusCodes.ACCOUNTING_RESPONSE, request.getIdentifier(), null);
        log.debug("计费应答: session={} type={}", sessionId, statusType);
        return resp;
    }

    private void publishAudit(RadiusPacket req, String userMask, Integer statusType) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("operator", "radius");
            event.put("operation", "RADIUS Accounting " + (statusType == null ? "?" : statusType));
            event.put("ip", ip4(req.getAttr(RadiusCodes.NAS_IP_ADDRESS)));
            Map<String, Object> params = new HashMap<>();
            params.put("user", userMask);
            params.put("mac", req.getString(RadiusCodes.CALLING_STATION_ID));
            params.put("sessionId", req.getString(RadiusCodes.ACCT_SESSION_ID));
            event.put("params", params);
            event.put("status", "SUCCESS");
            event.put("time", System.currentTimeMillis());
            kafkaTemplate.send(AUDIT_TOPIC, MAPPER.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("RADIUS 计费审计发送失败: {}", e.getMessage());
        }
    }

    private static Long toLong(Integer i) { return i == null ? null : i.longValue(); }

    /** 4 字节 IP 属性 → 点分十进制；非 4 字节返回 null。 */
    private static String ip4(byte[] a) {
        if (a == null || a.length != 4) return null;
        return (a[0] & 0xff) + "." + (a[1] & 0xff) + "." + (a[2] & 0xff) + "." + (a[3] & 0xff);
    }

    private static String mask(String u) {
        if (u == null) return null;
        if (u.length() <= 2) return "***";
        return u.charAt(0) + "***" + u.charAt(u.length() - 1);
    }
}
