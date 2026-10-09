package com.his.outpatient.service;

import com.his.common.exception.BusinessException;
import com.his.common.idempotent.Idempotent;
import com.his.common.result.ResultCode;
import com.his.outpatient.entity.*;
import com.his.outpatient.mapper.OutpatientMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class OutpatientService {

    @Autowired private OutpatientMapper mapper;

    public List<DoctorSchedule> listSchedules(String date) {
        return mapper.listSchedules(date);
    }

    /**
     * 挂号：
     * - 幂等：同患者同排班不可重复挂号（DB 唯一约束兜底 + 前置查询）
     * - 并发号源控制：SELECT FOR UPDATE 悲观锁 + 条件 UPDATE 防超卖
     */
    @Transactional(rollbackFor = Exception.class)
    @Idempotent(key = "'reg:' + #reg.patientId + '_' + #reg.scheduleId", expire = 5)
    public Registration register(Registration reg) {
        if (mapper.countDuplicateReg(reg.getPatientId(), reg.getScheduleId()) > 0) {
            throw new BusinessException(ResultCode.DUPLICATE_REGISTRATION);
        }
        // 悲观锁锁定号源行，防止并发超卖
        DoctorSchedule s = mapper.selectScheduleForUpdate(reg.getScheduleId());
        if (s == null) throw new BusinessException("号源不存在");
        if (s.getBookedCount() >= s.getTotalCount()) {
            throw new BusinessException(ResultCode.REGISTRATION_FULL);
        }
        int updated = mapper.incrBookedCount(reg.getScheduleId());
        if (updated == 0) throw new BusinessException(ResultCode.REGISTRATION_FULL);

        reg.setRegNo("R" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase());
        reg.setDoctorId(s.getDoctorId());
        reg.setDoctorName(s.getDoctorName());
        reg.setDepartment(s.getDepartment());
        reg.setFee(s.getFee());
        reg.setVisitDate(s.getVisitDate());
        reg.setTimeSlot(s.getTimeSlot());
        reg.setStatus("REGISTERED");
        mapper.insertRegistration(reg);
        log.info("挂号成功: {} 患者{} 医生{}", reg.getRegNo(), reg.getPatientName(), s.getDoctorName());
        return reg;
    }

    /** 开处方（医生工作站） */
    @Transactional(rollbackFor = Exception.class)
    public Prescription createPrescription(Prescription p, List<PrescriptionItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (PrescriptionItem it : items) {
            it.setSubtotal(it.getPrice().multiply(it.getQuantity()));
            total = total.add(it.getSubtotal());
        }
        p.setPrescriptionNo("RX" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase());
        p.setTotalAmount(total);
        p.setStatus("PENDING_PAY");
        mapper.insertPrescription(p);
        for (PrescriptionItem it : items) it.setPrescriptionId(p.getId());
        mapper.insertPrescriptionItems(items);
        log.info("处方开立: {} 金额{}", p.getPrescriptionNo(), total);
        return p;
    }

    public List<Prescription> listPatientPrescriptions(Long patientId) {
        return mapper.listByPatient(patientId);
    }

    public List<PrescriptionItem> listPrescriptionItems(Long prescriptionId) {
        return mapper.listItems(prescriptionId);
    }

    /** 缴费后置为待发药（实际由 billing 服务回调，此处提供接口供药房测试闭环） */
    @Transactional(rollbackFor = Exception.class)
    public int markPaid(Long prescriptionId) {
        return mapper.updatePrescriptionStatus(prescriptionId, "PAID");
    }
}
