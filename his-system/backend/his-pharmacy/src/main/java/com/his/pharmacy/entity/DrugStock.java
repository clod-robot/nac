package com.his.pharmacy.entity;

import lombok.Data;
import java.util.Date;

/** 药品库存（乐观锁防超发） */
@Data
public class DrugStock {
    private Long id;
    private Long drugId;
    private String batchNo;
    private Integer quantity;
    /** 乐观锁版本号 */
    private Integer version;
    private Date expireDate;
    private Date updateTime;
}
