package com.his.outpatient.entity;

import lombok.Data;
import java.math.BigDecimal;

/** 处方明细 */
@Data
public class PrescriptionItem {
    private Long id;
    private Long prescriptionId;
    private Long drugId;
    private String drugName;
    private String specification;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal price;
    private BigDecimal subtotal;
    /** 用法用量 */
    private String usage;
}
