package com.nac.radius.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nac.radius.entity.OnlineSession;
import com.nac.radius.entity.RadiusAcctRecord;
import com.nac.radius.mapper.OnlineSessionMapper;
import com.nac.radius.mapper.RadiusAcctRecordMapper;
import com.nac.radius.packet.RadiusCodes;
import com.nac.radius.packet.RadiusPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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

    public RadiusAcctService(OnlineSessionMapper sessionMapper, RadiusAcctRecordMapper acctMapper,
                             KafkaTemplate<String, String> kafkaTemplate) {
        this.sessionMapper = sessionMapper;
        this.acctMapper = acctMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    public RadiusPacket account(RadiusPacket request) {
        Integer statusType = request.getInt(RadiusCodes.ACCT_STATUS_TYPE);
        String sessionId = request.getString(RadiusCodes.ACCT_SESSION_ID);
        String username = mask(request.getString(RadiusCodes.USER_NAME));
        String mac = request.getString(RadiusCodes.CALLING_STATION_ID);
        String nasIp = request.getString(RadiusCodes.NAS_IP_ADDRESS);

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
                    OnlineSession s = new OnlineSession();
                    s.setAcctSessionId(sessionId);
                    s.setUsernameMask(username);
                    s.setMac(mac);
                    s.setNasIp(nasIp);
                    s.setFramedIp(request.getString(RadiusCodes.FRAMED_IP_ADDRESS));
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
            event.put("ip", req.getString(RadiusCodes.NAS_IP_ADDRESS));
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

    private static String mask(String u) {
        if (u == null) return null;
        if (u.length() <= 2) return "***";
        return u.charAt(0) + "***" + u.charAt(u.length() - 1);
    }
}
