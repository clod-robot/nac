package com.his.emr.service;

import com.his.common.exception.BusinessException;
import com.his.emr.entity.MedicalRecord;
import com.his.emr.mapper.MedicalRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 电子病历服务：集成 OnlyOffice 在线文档编辑。
 * OnlyOffice 配置（文档服务地址、密钥）通过环境变量注入。
 */
@Service
public class MedicalRecordService {

    @Autowired private MedicalRecordMapper mapper;

    @Value("${his.onlyoffice.server-url:${ONLYOFFICE_URL:http://127.0.0.1:8089}}")
    private String onlyOfficeUrl;

    @Value("${his.onlyoffice.secret:${ONLYOFFICE_SECRET:HisOnlyOffice@2026}}")
    private String onlyOfficeSecret;

    @Value("${his.onlyoffice.callback-url:http://his-emr/api/emr/onlyoffice/callback}")
    private String callbackUrl;

    public MedicalRecord create(MedicalRecord r) {
        r.setRecordNo("MR" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase());
        r.setDocumentKey(UUID.randomUUID().toString());
        r.setStatus("DRAFT");
        mapper.insert(r);
        return r;
    }

    public MedicalRecord update(MedicalRecord r) {
        if (mapper.selectById(r.getId()) == null) throw new BusinessException("病历不存在");
        mapper.update(r);
        return r;
    }

    public List<MedicalRecord> listByPatient(Long patientId) {
        return mapper.listByPatient(patientId, 20);
    }

    /**
     * 生成 OnlyOffice 编辑器配置（前端加载编辑器时使用）。
     * 密钥通过配置注入，document.key 必须唯一标识文档版本。
     */
    public Map<String, Object> onlyOfficeConfig(Long recordId, String userName) {
        MedicalRecord r = mapper.selectById(recordId);
        if (r == null) throw new BusinessException("病历不存在");
        Map<String, Object> config = new HashMap<>();
        config.put("document", Map.of(
                "fileType", "docx",
                "key", r.getDocumentKey(),
                "title", r.getRecordNo() + ".docx",
                "url", r.getDocumentUrl() != null ? r.getDocumentUrl() : ""
        ));
        config.put("documentType", "word");
        config.put("editorConfig", Map.of(
                "callbackUrl", callbackUrl + "?key=" + r.getDocumentKey(),
                "user", Map.of("id", userName, "name", userName),
                "mode", "edit",
                "lang", "zh-CN"
        ));
        config.put("token", onlyOfficeSecret); // JWT 签名场景使用，此处简化
        return config;
    }

    /** OnlyOffice 保存回调（status==2 表示已保存） */
    public void onlyOfficeCallback(String key, String url, int status) {
        if (status == 2 || status == 6) {
            mapper.updateDocumentUrl(key, url);
        }
    }
}
