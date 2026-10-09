package com.his.emr.mapper;

import com.his.emr.entity.MedicalRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MedicalRecordMapper {

    @Insert("INSERT INTO medical_record(record_no, patient_id, patient_name, doctor_id, doctor_name, " +
            "registration_id, type, chief_complaint, present_illness, past_history, diagnosis, treatment, " +
            "document_key, document_url, status, create_time, update_time) VALUES(#{recordNo}, #{patientId}, " +
            "#{patientName}, #{doctorId}, #{doctorName}, #{registrationId}, #{type}, #{chiefComplaint}, " +
            "#{presentIllness}, #{pastHistory}, #{diagnosis}, #{treatment}, #{documentKey}, #{documentUrl}, " +
            "#{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MedicalRecord r);

    @Update("UPDATE medical_record SET chief_complaint=#{chiefComplaint}, present_illness=#{presentIllness}, " +
            "past_history=#{pastHistory}, diagnosis=#{diagnosis}, treatment=#{treatment}, " +
            "document_key=#{documentKey}, document_url=#{documentUrl}, status=#{status}, update_time=NOW() WHERE id=#{id}")
    int update(MedicalRecord r);

    @Select("SELECT * FROM medical_record WHERE patient_id = #{patientId} ORDER BY id DESC LIMIT #{limit}")
    List<MedicalRecord> listByPatient(@Param("patientId") Long patientId, @Param("limit") int limit);

    @Select("SELECT * FROM medical_record WHERE id = #{id}")
    MedicalRecord selectById(@Param("id") Long id);

    @Update("UPDATE medical_record SET document_url=#{url}, update_time=NOW() WHERE document_key=#{key}")
    int updateDocumentUrl(@Param("key") String key, @Param("url") String url);
}
