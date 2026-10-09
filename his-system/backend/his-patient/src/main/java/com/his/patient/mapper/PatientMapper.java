package com.his.patient.mapper;

import com.his.patient.entity.Patient;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PatientMapper {

    @Select("SELECT * FROM patient WHERE id = #{id}")
    Patient selectById(@Param("id") Long id);

    @Select("SELECT * FROM patient WHERE patient_no = #{patientNo}")
    Patient selectByPatientNo(@Param("patientNo") String patientNo);

    @Select("SELECT * FROM patient ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Patient> selectPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(*) FROM patient")
    long count();

    @Insert("INSERT INTO patient(patient_no, name, id_card, phone, gender, birth_date, address, " +
            "allergy_history, create_time, update_time) VALUES(#{patientNo}, #{name}, #{idCard}, " +
            "#{phone}, #{gender}, #{birthDate}, #{address}, #{allergyHistory}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Patient patient);

    @Update("UPDATE patient SET name=#{name}, id_card=#{idCard}, phone=#{phone}, gender=#{gender}, " +
            "birth_date=#{birthDate}, address=#{address}, allergy_history=#{allergyHistory}, update_time=NOW() WHERE id=#{id}")
    int update(Patient patient);
}
