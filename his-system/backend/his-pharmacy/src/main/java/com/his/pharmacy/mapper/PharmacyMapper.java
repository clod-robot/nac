package com.his.pharmacy.mapper;

import com.his.pharmacy.entity.DispenseRecord;
import com.his.pharmacy.entity.Drug;
import com.his.pharmacy.entity.DrugStock;
import com.his.pharmacy.vo.PrescriptionView;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface PharmacyMapper {

    // ===== 处方查询（只读视图，跨服务数据由 outpatient 库同库部署时直接查；生产应走 Feign） =====
    @Select("SELECT p.id, p.prescription_no AS prescriptionNo, p.patient_id AS patientId, " +
            "p.patient_name AS patientName, p.doctor_name AS doctorName, p.type, p.total_amount AS totalAmount, " +
            "p.status, p.diagnosis, p.create_time AS createTime " +
            "FROM prescription p WHERE p.status = #{status} ORDER BY p.create_time ASC LIMIT #{limit}")
    List<PrescriptionView> listPrescriptionsByStatus(@Param("status") String status, @Param("limit") int limit);

    @Select("SELECT id, prescription_id AS prescriptionId, drug_id AS drugId, drug_name AS drugName, " +
            "specification, quantity, unit, price, subtotal, usage FROM prescription_item WHERE prescription_id = #{prescriptionId}")
    List<PrescriptionView.Item> listPrescriptionItems(@Param("prescriptionId") Long prescriptionId);

    @Select("SELECT id, prescription_no AS prescriptionNo, patient_name AS patientName, status, total_amount AS totalAmount, " +
            "doctor_name AS doctorName FROM prescription WHERE id = #{id}")
    PrescriptionView selectPrescription(@Param("id") Long id);

    @Update("UPDATE prescription SET status = #{status}, update_time = NOW() WHERE id = #{id} AND status = #{expected}")
    int casUpdatePrescriptionStatus(@Param("id") Long id, @Param("expected") String expected, @Param("status") String status);

    // ===== 药品 =====
    @Select("SELECT * FROM drug WHERE status = 1 ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Drug> listDrugs(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(*) FROM drug WHERE status = 1")
    long countDrugs();

    @Select("SELECT id, drug_id AS drugId, batch_no AS batchNo, quantity, version, expire_date AS expireDate " +
            "FROM drug_stock WHERE drug_id = #{drugId} ORDER BY expire_date ASC LIMIT 1")
    DrugStock selectStockByDrugId(@Param("drugId") Long drugId);

    /**
     * 乐观锁扣减库存：version CAS + quantity 条件，保证并发不超发。
     * 返回 0 表示库存不足或版本冲突，需重试。
     */
    @Update("UPDATE drug_stock SET quantity = quantity - #{qty}, version = version + 1, update_time = NOW() " +
            "WHERE drug_id = #{drugId} AND quantity >= #{qty} AND version = #{version}")
    int deductStock(@Param("drugId") Long drugId, @Param("qty") BigDecimal qty, @Param("version") Integer version);

    // ===== 发药流水 =====
    @Insert("INSERT INTO dispense_record(dispense_no, prescription_id, prescription_no, patient_id, patient_name, " +
            "drug_id, drug_name, quantity, unit, pharmacist_id, pharmacist_name, window_no, dispense_time) " +
            "VALUES(#{dispenseNo}, #{prescriptionId}, #{prescriptionNo}, #{patientId}, #{patientName}, " +
            "#{drugId}, #{drugName}, #{quantity}, #{unit}, #{pharmacistId}, #{pharmacistName}, #{windowNo}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDispenseRecord(DispenseRecord record);

    @Select("SELECT DATE(dispense_time) AS day, COUNT(*) AS cnt, COALESCE(SUM(quantity),0) AS qty " +
            "FROM dispense_record WHERE dispense_time >= #{since} GROUP BY DATE(dispense_time) ORDER BY day")
    List<Map<String, Object>> statsDaily(@Param("since") Date since);

    @Select("SELECT drug_name AS name, COALESCE(SUM(quantity),0) AS qty FROM dispense_record " +
            "WHERE dispense_time >= #{since} GROUP BY drug_name ORDER BY qty DESC LIMIT 10")
    List<Map<String, Object>> statsTopDrugs(@Param("since") Date since);

    @Select("SELECT COUNT(*) FROM dispense_record WHERE prescription_id = #{prescriptionId}")
    int countDispensedByPrescription(@Param("prescriptionId") Long prescriptionId);
}
