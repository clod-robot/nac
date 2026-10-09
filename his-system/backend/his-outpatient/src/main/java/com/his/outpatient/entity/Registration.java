package com.his.outpatient.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/** 挂号记录 */
@Data
public class Registration {
    private Long id;
    private String regNo;
    private Long patientId;
    private String patientName;
    private Long scheduleId;
    private Long doctorId;
    private String doctorName;
    private String department;
    private BigDecimal fee;
    /** 状态：REGISTERED(已挂号)/VISITING(就诊中)/COMPLETED(已完成)/CANCELED(已退号) */
    private String status;
    private Date visitDate;
    private String timeSlot;
    private Date createTime;
}
