package com.nac.common.exception;

import com.nac.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Spring MVC 侧未知路径 404：Spring 6.1 对无映射路径抛 NoResourceFoundException，
 * 此前被 GlobalExceptionHandler 的 Exception 兜底统一映射成 500（语义错误）。
 *
 * 注意：该类与 WebFlux 的 org.springframework.web.reactive.resource.NoResourceFoundException
 * 同名不同类；网关运行时无 spring-webmvc，故用独立 Advice + 条件装配，
 * 条件基于 ASM 元数据判定，类不存在时直接跳过、不触发类加载，网关不受影响
 * （网关未知路径由 GatewayNotFoundExceptionHandler 兜底）。
 *
 * 必须 HIGHEST_PRECEDENCE：否则无序时按注册顺序，GlobalExceptionHandler 的
 * Exception.class 兜底会先命中（跨 Advice 只比较先后、不比较方法精确度）。
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(name = "org.springframework.web.servlet.resource.NoResourceFoundException")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MvcNoResourceFoundExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> notFound(NoResourceFoundException e) {
        if (log.isDebugEnabled()) {
            log.debug("未知路径: {}", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(404, "接口不存在"));
    }
}
