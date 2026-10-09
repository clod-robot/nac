package com.his.billing.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface BillingMapper {

    /** 处方缴费：PENDING_PAY -> PAID，供药房发药闭环使用 */
    @Update("UPDATE prescription SET status='PAID', update_time=NOW() " +
            "WHERE id=#{prescriptionId} AND status='PENDING_PAY'")
    int payPrescription(@Param("prescriptionId") Long prescriptionId);

    @Insert("INSERT INTO payment(payment_no, prescription_id, amount, pay_method, status, create_time) " +
            "VALUES(#{paymentNo}, #{prescriptionId}, #{amount}, #{payMethod}, 'SUCCESS', NOW())")
    int insertPayment(@Param("paymentNo") String paymentNo, @Param("prescriptionId") Long prescriptionId,
                      @Param("amount") java.math.BigDecimal amount, @Param("payMethod") String payMethod);

    @Select("SELECT total_amount FROM prescription WHERE id=#{id}")
    java.math.BigDecimal selectPrescriptionAmount(@Param("id") Long id);
}
