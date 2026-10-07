package com.nac.log.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nac.common.syslog.SyslogForwarder;
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
    private final SyslogForwarder syslogForwarder;

    public OpLogConsumer(SysLogMapper sysLogMapper, SyslogForwarder syslogForwarder) {
        this.sysLogMapper = sysLogMapper;
        this.syslogForwarder = syslogForwarder;
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
            // 外发到 syslog 服务器（静默降级，不影响落库）
            syslogForwarder.forwardOpLog(formatOp(sl));
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

    private static String formatOp(SysLog sl) {
        StringBuilder sb = new StringBuilder("opLog ");
        sb.append("user=").append(nz(sl.getOperator())).append('/').append(sl.getOperatorId());
        sb.append(" role=").append(nz(sl.getRole()));
        sb.append(" ip=").append(nz(sl.getIp()));
        sb.append(" method=").append(nz(sl.getMethod()));
        sb.append(" op=").append(nz(sl.getOperation()));
        sb.append(" status=").append(nz(sl.getStatus()));
        sb.append(" cost=").append(sl.getCostMs()).append("ms");
        return sb.toString();
    }

    private static String nz(String s) { return s == null ? "-" : s; }
}
