package com.his.patient.service;

import com.his.common.security.AesCryptoUtil;
import com.his.common.security.SensitiveUtil;
import com.his.patient.entity.Patient;
import com.his.patient.mapper.PatientMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 患者服务：敏感字段（身份证、手机号）入库前 AES-256-GCM 加密，
 * 列表展示脱敏，详情可解密查看（生产环境应加角色权限控制，此处演示）。
 */
@Service
public class PatientService {

    @Autowired private PatientMapper patientMapper;
    @Autowired private AesCryptoUtil aesCryptoUtil;

    public Patient create(Patient p) {
        p.setPatientNo("P" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        p.setIdCard(aesCryptoUtil.encrypt(p.getIdCard()));
        p.setPhone(aesCryptoUtil.encrypt(p.getPhone()));
        patientMapper.insert(p);
        return p;
    }

    public Patient detail(Long id) {
        Patient p = patientMapper.selectById(id);
        if (p == null) return null;
        // 解密返回（接口受网关 token + 角色控制）
        p.setIdCard(aesCryptoUtil.decrypt(p.getIdCard()));
        p.setPhone(aesCryptoUtil.decrypt(p.getPhone()));
        return p;
    }

    /** 列表：脱敏展示 */
    public List<Patient> page(int limit, int offset) {
        List<Patient> list = patientMapper.selectPage(limit, offset);
        for (Patient p : list) {
            p.setIdCard(SensitiveUtil.maskIdCard(aesCryptoUtil.decrypt(p.getIdCard())));
            p.setPhone(SensitiveUtil.maskPhone(aesCryptoUtil.decrypt(p.getPhone())));
            p.setName(SensitiveUtil.maskName(p.getName()));
        }
        return list;
    }

    public long count() { return patientMapper.count(); }

    public Patient update(Patient p) {
        Patient existing = patientMapper.selectById(p.getId());
        if (existing == null) return null;
        if (p.getIdCard() != null) p.setIdCard(aesCryptoUtil.encrypt(p.getIdCard()));
        if (p.getPhone() != null) p.setPhone(aesCryptoUtil.encrypt(p.getPhone()));
        patientMapper.update(p);
        return p;
    }
}
