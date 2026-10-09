package com.his.auth.entity;

import lombok.Data;
import java.util.Date;

@Data
public class SysUser {
    private Long id;
    private String username;
    private String password;
    private String realName;
    /** 角色：admin/doctor/pharmacist/nurse */
    private String role;
    private String phone;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}
