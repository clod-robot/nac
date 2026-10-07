package com.nac.log.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nac.log.entity.SysLog;
import com.nac.log.mapper.SysLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 操作日志消费者：订阅 nac-op-log，解析后落库。
 * 消费失败仅记录告警，不抛出避免重复消费风暴。
 */
@Slf4j
@Component
public class OpLogConsumer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SysLogMapper sysLogMapper;

    public OpLogConsumer(SysLogMapper sysLogMapper) {
        this.sysLogMapper = sysLogMapper;
    }

    @KafkaListener(topics = "nac-op-log", groupId = "nac-log")
    public void onMessage(String payload) {
        try {
            Map<?, ?> event = MAPPER.readValue(payload, Map.class);
            SysLog sl = new SysLog();
            sl.setOperator(str(event.get("operator")));
            sl.setOperatorId(toLong(event.get("operatorId")));
            sl.setRole(str(event.get("role")));
            sl.setIp(str(event.get("ip")));
            sl.setMethod(truncate(str(event.get("method")), 128));
            sl.setOperation(truncate(str(event.get("operation")), 255));
            sl.setParams(event.get("params") == null ? null : truncate(MAPPER.writeValueAsString(event.get("params")), 60000));
            sl.setStatus(truncate(str(event.get("status")), 32));
            sl.setCostMs(toInt(event.get("costMs")));
            Object t = event.get("time");
            if (t instanceof Number n) {
                sl.setCreateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(n.longValue()), ZoneId.systemDefault()));
            } else {
                sl.setCreateTime(LocalDateTime.now());
            }
            sysLogMapper.insert(sl);
        } catch (Exception e) {
            log.warn("操作日志消费失败: {}", e.getMessage());
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.valueOf(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
