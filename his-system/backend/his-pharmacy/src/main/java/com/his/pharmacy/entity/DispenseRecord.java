package com.his.pharmacy.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/** 发药流水 */
@Data
public class DispenseRecord {
    private Long id;
    private String dispenseNo;
    private Long prescriptionId;
    private String prescriptionNo;
    private Long patientId;
    private String patientName;
    private Long drugId;
    private String drugName;
    private BigDecimal quantity;
    private String unit;
    /** 发药药师 */
    private Long pharmacistId;
    private String pharmacistName;
    /** 发药窗口 */
    private String windowNo;
    private Date dispenseTime;
}
