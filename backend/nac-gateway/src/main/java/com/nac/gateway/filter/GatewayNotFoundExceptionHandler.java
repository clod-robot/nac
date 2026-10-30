package com.nac.gateway.filter;

import com.nac.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.resource.NoResourceFoundException;

/**
 * 网关未知路径处理：
 * 路由/资源匹配失败时 Spring WebFlux 抛 NoResourceFoundException，
 * 此前被 nac-common 的全局 ExceptionHandler 统一映射成 500，
 * 导致 WAF/监控的 500 统计虚高、扫描噪音与真实故障无法区分。
 * 该 Advice 以最高优先级返回标准 404，与真实服务异常隔离。
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayNotFoundExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> notFound(NoResourceFoundException e) {
        if (log.isDebugEnabled()) {
            log.debug("未知路径: {}", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(404, "接口不存在"));
    }
}
