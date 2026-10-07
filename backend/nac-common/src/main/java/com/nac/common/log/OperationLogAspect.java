package com.nac.common.log;

import com.nac.common.context.UserContext;
import com.nac.common.security.SensitiveUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志切面：异步发送到 Kafka topic（nac-log 服务消费落库），敏感参数脱敏。
 * Kafka 不可用时降级为本地日志，不阻塞主链路。
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOPIC = "nac-op-log";

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaProvider;

    public OperationLogAspect(ObjectProvider<KafkaTemplate<String, String>> kafkaProvider) {
        this.kafkaProvider = kafkaProvider;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result;
        String status = "SUCCESS";
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            status = "FAIL:" + t.getMessage();
            throw t;
        } finally {
            try {
                record(pjp, operationLog, status, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("操作日志记录失败", e);
            }
        }
    }

    private void record(ProceedingJoinPoint pjp, OperationLog op, String status, long cost) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("operator", UserContext.getUsername() != null ? UserContext.getUsername() : "anonymous");
        event.put("operatorId", UserContext.getUserId());
        event.put("role", UserContext.getRole());
        event.put("ip", getClientIp());
        event.put("method", pjp.getSignature().toShortString());
        event.put("operation", op.value());
        event.put("params", maskParams(pjp.getArgs()));
        event.put("status", status);
        event.put("costMs", cost);
        event.put("time", System.currentTimeMillis());

        KafkaTemplate<String, String> kafka = kafkaProvider.getIfAvailable();
        if (kafka != null) {
            try {
                kafka.send(TOPIC, MAPPER.writeValueAsString(event));
            } catch (Exception e) {
                log.warn("操作日志 Kafka 发送失败，降级本地日志: {}", event, e);
            }
        } else {
            log.info("OP-LOG {}", event);
        }
    }

    private Object maskParams(Object[] args) {
        if (args == null) return null;
        Object[] masked = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            masked[i] = maskValue(args[i]);
        }
        return masked;
    }

    private Object maskValue(Object v) {
        if (v == null) return null;
        if (v instanceof String s) {
            // 疑似手机号脱敏
            if (s.matches("1\\d{10}")) return SensitiveUtil.maskPhone(s);
            return s;
        }
        if (v instanceof Map<?, ?> map) {
            Map<String, Object> out = new HashMap<>();
            map.forEach((k, val) -> {
                String key = String.valueOf(k).toLowerCase();
                if (key.contains("password") || key.contains("secret") || key.contains("captcha") || key.contains("code")) {
                    out.put(String.valueOf(k), "******");
                } else if (val instanceof String sv && sv.matches("1\\d{10}")) {
                    out.put(String.valueOf(k), SensitiveUtil.maskPhone(sv));
                } else {
                    out.put(String.valueOf(k), val);
                }
            });
            return out;
        }
        return v;
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";
            HttpServletRequest req = attrs.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
            return req.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
