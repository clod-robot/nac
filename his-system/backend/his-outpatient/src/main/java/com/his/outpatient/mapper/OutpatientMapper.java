package com.his.outpatient.mapper;

import com.his.outpatient.entity.DoctorSchedule;
import com.his.outpatient.entity.Prescription;
import com.his.outpatient.entity.PrescriptionItem;
import com.his.outpatient.entity.Registration;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OutpatientMapper {

    // ===== 号源（医生排班） =====
    @Select("SELECT id, doctor_id AS doctorId, doctor_name AS doctorName, department, visit_date AS visitDate, " +
            "time_slot AS timeSlot, total_count AS totalCount, booked_count AS bookedCount, fee, status " +
            "FROM doctor_schedule WHERE id = #{id} FOR UPDATE")
    DoctorSchedule selectScheduleForUpdate(@Param("id") Long id);

    @Update("UPDATE doctor_schedule SET booked_count = booked_count + 1 WHERE id = #{id} AND booked_count < total_count")
    int incrBookedCount(@Param("id") Long id);

    @Select("SELECT * FROM doctor_schedule WHERE visit_date = #{date} AND status = 'OPEN' ORDER BY department")
    List<DoctorSchedule> listSchedules(@Param("date") String date);

    // ===== 挂号 =====
    @Insert("INSERT INTO registration(reg_no, patient_id, patient_name, schedule_id, doctor_id, doctor_name, " +
            "department, fee, status, visit_date, time_slot, create_time) VALUES(#{regNo}, #{patientId}, " +
            "#{patientName}, #{scheduleId}, #{doctorId}, #{doctorName}, #{department}, #{fee}, #{status}, " +
            "#{visitDate}, #{timeSlot}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRegistration(Registration reg);

    @Select("SELECT * FROM registration WHERE id = #{id}")
    Registration selectRegById(@Param("id") Long id);

    @Update("UPDATE registration SET status = #{status} WHERE id = #{id}")
    int updateRegStatus(@Param("id") Long id, @Param("status") String status);

    @Select("SELECT count(*) FROM registration WHERE patient_id = #{patientId} AND schedule_id = #{scheduleId} " +
            "AND status IN ('REGISTERED','VISITING')")
    int countDuplicateReg(@Param("patientId") Long patientId, @Param("scheduleId") Long scheduleId);

    // ===== 处方 =====
    @Insert("INSERT INTO prescription(prescription_no, patient_id, patient_name, doctor_id, doctor_name, " +
            "registration_id, type, total_amount, status, diagnosis, create_time, update_time) " +
            "VALUES(#{prescriptionNo}, #{patientId}, #{patientName}, #{doctorId}, #{doctorName}, " +
            "#{registrationId}, #{type}, #{totalAmount}, #{status}, #{diagnosis}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPrescription(Prescription p);

    @Insert("<script>INSERT INTO prescription_item(prescription_id, drug_id, drug_name, specification, " +
            "quantity, unit, price, subtotal, usage) VALUES " +
            "<foreach collection='items' item='it' separator=','>" +
            "(#{it.prescriptionId}, #{it.drugId}, #{it.drugName}, #{it.specification}, #{it.quantity}, " +
            "#{it.unit}, #{it.price}, #{it.subtotal}, #{it.usage})</foreach></script>")
    int insertPrescriptionItems(@Param("items") List<PrescriptionItem> items);

    @Select("SELECT * FROM prescription WHERE patient_id = #{patientId} ORDER BY id DESC LIMIT 20")
    List<Prescription> listByPatient(@Param("patientId") Long patientId);

    @Select("SELECT * FROM prescription_item WHERE prescription_id = #{prescriptionId}")
    List<PrescriptionItem> listItems(@Param("prescriptionId") Long prescriptionId);

    @Update("UPDATE prescription SET status = #{status}, update_time = NOW() WHERE id = #{id} AND status != #{status}")
    int updatePrescriptionStatus(@Param("id") Long id, @Param("status") String status);

    @Select("SELECT * FROM prescription WHERE id = #{id}")
    Prescription selectPrescriptionById(@Param("id") Long id);
}
