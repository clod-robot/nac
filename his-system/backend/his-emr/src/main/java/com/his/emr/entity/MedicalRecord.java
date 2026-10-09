package com.his.emr.entity;

import lombok.Data;
import java.util.Date;

/** 电子病历 */
@Data
public class MedicalRecord {
    private Long id;
    private String recordNo;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private Long registrationId;
    /** 病历类型：门诊/住院 */
    private String type;
    private String chiefComplaint;
    private String presentIllness;
    private String pastHistory;
    private String diagnosis;
    private String treatment;
    /** OnlyOffice 文档 key（在线编辑协同用） */
    private String documentKey;
    /** 文档存储地址 */
    private String documentUrl;
    private String status;
    private Date createTime;
    private Date updateTime;
}
