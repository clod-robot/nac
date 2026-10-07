package com.nac.log.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysLog {
    private Long id;
    private String operator;
    private Long operatorId;
    private String role;
    private String ip;
    private String method;
    private String operation;
    private String params;
    private String status;
    private Integer costMs;
    private LocalDateTime createTime;
}
