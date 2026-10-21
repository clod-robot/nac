package com.nac.log.entity;

import lombok.Data;

/**
 * 系统用户（查询视图），对应 sys_user。仅用于安全溯源的责任人反查，只读取必要展示字段。
 */
@Data
public class SysUserBrief {
    private Long id;
    private String username;
    private String realName;
    private String dept;
    private String roleCode;
    private Integer status;
    /** 手机号脱敏展示（如 138****1234），由 phone_blind 直接返回 */
    private String phoneBlind;
}
