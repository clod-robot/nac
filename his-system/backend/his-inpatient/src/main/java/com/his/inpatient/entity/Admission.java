package com.his.inpatient.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/** 入院登记 */
@Data
public class Admission {
    private Long id;
    private String admissionNo;
    private Long patientId;
    private String patientName;
    private String department;
    private Long bedId;
    private String bedNo;
    private Long doctorId;
    private String doctorName;
    private String diagnosis;
    private BigDecimal deposit;
    /** ADMITTED(在院)/DISCHARGED(已出院) */
    private String status;
    private Date admitTime;
    private Date dischargeTime;
    private Date createTime;
}
