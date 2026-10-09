package com.his.common.result;

import lombok.Data;
import java.io.Serializable;

/**
 * 统一响应结构
 */
@Data
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();
    /** 链路追踪ID，便于日志排查 */
    private String traceId;

    public Result() {}

    public static <T> Result<T> ok() {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> ok(T data) {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(String message) {
        return build(ResultCode.FAIL.getCode(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return build(code, message, null);
    }

    public static <T> Result<T> fail(ResultCode rc) {
        return build(rc.getCode(), rc.getMessage(), null);
    }

    private static <T> Result<T> build(int code, String message, T data) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.data = data;
        return r;
    }

    public boolean success() {
        return ResultCode.SUCCESS.getCode() == this.code;
    }
}
