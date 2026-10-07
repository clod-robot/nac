package com.nac.common.exception;

import com.nac.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：携带 ResultCode，由全局异常处理器统一转 Result。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }

    public BusinessException(ResultCode rc, String message) {
        super(message);
        this.code = rc.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
