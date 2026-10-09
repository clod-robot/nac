package com.his.outpatient.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/** 处方主表（医生开立，药房发药） */
@Data
public class Prescription {
    private Long id;
    private String prescriptionNo;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private Long registrationId;
    /** 处方类型：西药/中成药/中药 */
    private String type;
    private BigDecimal totalAmount;
    /** 状态：PENDING_PAY(待缴费)/PAID(已缴费待发药)/DISPENSED(已发药)/REVOKED(已作废) */
    private String status;
    private String diagnosis;
    private Date createTime;
    private Date updateTime;
}
