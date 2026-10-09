package com.his.inpatient.mapper;

import com.his.inpatient.entity.Admission;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface InpatientMapper {

    @Insert("INSERT INTO admission(admission_no, patient_id, patient_name, department, bed_id, bed_no, " +
            "doctor_id, doctor_name, diagnosis, deposit, status, admit_time, create_time) " +
            "VALUES(#{admissionNo}, #{patientId}, #{patientName}, #{department}, #{bedId}, #{bedNo}, " +
            "#{doctorId}, #{doctorName}, #{diagnosis}, #{deposit}, 'ADMITTED', NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAdmission(Admission a);

    @Update("UPDATE bed SET status = 'OCCUPIED', patient_id = #{patientId} WHERE id = #{bedId} AND status = 'FREE'")
    int occupyBed(@Param("bedId") Long bedId, @Param("patientId") Long patientId);

    @Update("UPDATE admission SET status='DISCHARGED', discharge_time=NOW() WHERE id=#{id} AND status='ADMITTED'")
    int discharge(@Param("id") Long id);

    @Update("UPDATE bed SET status='FREE', patient_id=NULL WHERE id=#{bedId}")
    int releaseBed(@Param("bedId") Long bedId);

    @Select("SELECT id, bed_no AS bedNo, department, status FROM bed WHERE status = 'FREE' ORDER BY id")
    List<java.util.Map<String, Object>> listFreeBeds();

    @Select("SELECT * FROM admission WHERE status='ADMITTED' ORDER BY id DESC LIMIT 50")
    List<Admission> listAdmitted();
}
