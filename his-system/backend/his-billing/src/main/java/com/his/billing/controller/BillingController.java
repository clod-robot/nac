package com.his.billing.controller;

import com.his.billing.mapper.BillingMapper;
import com.his.common.exception.BusinessException;
import com.his.common.idempotent.Idempotent;
import com.his.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @Autowired private BillingMapper billingMapper;

    /** 处方缴费（幂等）：成功后处方状态变为 PAID，药房即可发药 */
    @PostMapping("/pay/{prescriptionId}")
    @Idempotent(key = "'pay:' + #prescriptionId", expire = 10, message = "缴费处理中，请勿重复提交")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> pay(@PathVariable Long prescriptionId, @RequestParam(defaultValue = "CASH") String payMethod) {
        BigDecimal amount = billingMapper.selectPrescriptionAmount(prescriptionId);
        if (amount == null) throw new BusinessException("处方不存在");
        String paymentNo = "PAY" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
        billingMapper.insertPayment(paymentNo, prescriptionId, amount, payMethod);
        int ok = billingMapper.payPrescription(prescriptionId);
        if (ok == 0) throw new BusinessException("处方缴费失败：状态已变更或不存在");
        return Result.ok();
    }
}
