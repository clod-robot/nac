package com.his.pharmacy.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** 药房视角的处方视图 */
@Data
public class PrescriptionView {
    private Long id;
    private String prescriptionNo;
    private Long patientId;
    private String patientName;
    private String doctorName;
    private String type;
    private BigDecimal totalAmount;
    private String status;
    private String diagnosis;
    private Date createTime;
    private List<Item> items;
    /** 库存校验结果：发药前预检查 */
    private Boolean stockSufficient;

    @Data
    public static class Item {
        private Long id;
        private Long prescriptionId;
        private Long drugId;
        private String drugName;
        private String specification;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal price;
        private BigDecimal subtotal;
        private String usage;
        /** 当前库存（预检查填充） */
        private Integer stockQuantity;
    }
}
