package com.his.patient.entity;

import lombok.Data;
import java.util.Date;

@Data
public class Patient {
    private Long id;
    private String patientNo;
    private String name;
    /** 身份证号（加密存储） */
    private String idCard;
    /** 手机号（加密存储） */
    private String phone;
    private String gender;
    private Date birthDate;
    private String address;
    /** 过敏史 */
    private String allergyHistory;
    private Date createTime;
    private Date updateTime;
}
