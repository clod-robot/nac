package com.his.inpatient.service;

import com.his.common.exception.BusinessException;
import com.his.inpatient.entity.Admission;
import com.his.inpatient.mapper.InpatientMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InpatientService {

    @Autowired private InpatientMapper mapper;

    /** 入院登记 + 床位分配（并发安全：bed 状态 CAS 占用） */
    @Transactional(rollbackFor = Exception.class)
    public Admission admit(Admission a) {
        int occupied = mapper.occupyBed(a.getBedId(), a.getPatientId());
        if (occupied == 0) throw new BusinessException("床位已被占用，请刷新重试");
        a.setAdmissionNo("A" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase());
        mapper.insertAdmission(a);
        return a;
    }

    @Transactional(rollbackFor = Exception.class)
    public void discharge(Long admissionId, Long bedId) {
        int ok = mapper.discharge(admissionId);
        if (ok == 0) throw new BusinessException("出院状态异常，请刷新");
        mapper.releaseBed(bedId);
    }

    public List<Map<String, Object>> freeBeds() { return mapper.listFreeBeds(); }
    public List<Admission> admitted() { return mapper.listAdmitted(); }
}
