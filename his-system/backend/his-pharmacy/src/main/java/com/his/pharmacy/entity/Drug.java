package com.his.pharmacy.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/** 药品目录 */
@Data
public class Drug {
    private Long id;
    private String drugCode;
    private String drugName;
    private String specification;
    private String manufacturer;
    private String dosageForm;
    private BigDecimal price;
    private String unit;
    /** 处方类型：RX(西药)/ZCY(中成药)/ZY(中药) */
    private String category;
    private Integer status;
    private Date createTime;
}
