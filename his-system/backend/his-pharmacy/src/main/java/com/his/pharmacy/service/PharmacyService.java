package com.his.pharmacy.service;

import com.his.common.context.UserContext;
import com.his.common.exception.BusinessException;
import com.his.common.idempotent.Idempotent;
import com.his.common.redis.RedisUtil;
import com.his.common.result.ResultCode;
import com.his.pharmacy.entity.DispenseRecord;
import com.his.pharmacy.entity.Drug;
import com.his.pharmacy.entity.DrugStock;
import com.his.pharmacy.mapper.PharmacyMapper;
import com.his.pharmacy.vo.PrescriptionView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 药房发药核心服务：
 * 1. 待发药处方列表
 * 2. 处方审核（通过/驳回）
 * 3. 发药：逐项乐观锁扣减库存（CAS 防超发）+ 发药流水 + 处方状态 CAS 流转 + 幂等
 * 4. 发药统计（ECharts 数据）
 * 库存不足时整体回滚，避免部分发药。
 */
@Slf4j
@Service
public class PharmacyService {

    @Autowired private PharmacyMapper mapper;
    @Autowired private RedisUtil redisUtil;
    @Autowired(required = false) private KafkaTemplate<String, String> kafkaTemplate;

    /** 待发药处方列表（默认只取已缴费 PAID 状态） */
    public List<PrescriptionView> listPendingDispense(int limit) {
        return mapper.listPrescriptionsByStatus("PAID", Math.min(limit, 100));
    }

    /** 处方详情 + 库存预检查 */
    public PrescriptionView prescriptionDetail(Long prescriptionId) {
        PrescriptionView p = mapper.selectPrescription(prescriptionId);
        if (p == null) throw new BusinessException("处方不存在");
        List<PrescriptionView.Item> items = mapper.listPrescriptionItems(prescriptionId);
        boolean sufficient = true;
        for (PrescriptionView.Item item : items) {
            DrugStock stock = mapper.selectStockByDrugId(item.getDrugId());
            int sq = stock == null ? 0 : stock.getQuantity();
            item.setStockQuantity(sq);
            if (BigDecimal.valueOf(sq).compareTo(item.getQuantity()) < 0) sufficient = false;
        }
        p.setItems(items);
        p.setStockSufficient(sufficient);
        return p;
    }

    /** 审核：APPROVED 通过（仍为 PAID 待发药，记录审核意见）或 REJECTED 驳回 */
    @Transactional(rollbackFor = Exception.class)
    public void review(Long prescriptionId, boolean approved, String opinion) {
        PrescriptionView p = mapper.selectPrescription(prescriptionId);
        if (p == null) throw new BusinessException("处方不存在");
        if (!"PAID".equals(p.getStatus())) throw new BusinessException("仅已缴费处方可审核");
        if (!approved) {
            int ok = mapper.casUpdatePrescriptionStatus(prescriptionId, "PAID", "REVIEW_REJECTED");
            if (ok == 0) throw new BusinessException("处方状态已变更，请刷新");
        }
        // 通过：维持 PAID，等待发药（审核意见可落扩展表，此处略）
        log.info("处方审核: {} approved={} by {}", prescriptionId, approved, UserContext.getUsername());
    }

    /**
     * 发药（核心）：
     * - 幂等：同一处方 10 秒内重复请求直接返回首次结果（Redis SETNX）
     * - 处方状态 CAS：PAID -> DISPENSED，防止重复发药
     * - 库存 CAS：逐项乐观锁扣减，失败整体回滚
     * - 发药流水逐条记录
     * - 异步发送 Kafka 事件（库存预警/统计），不阻塞主流程
     */
    @Transactional(rollbackFor = Exception.class)
    @Idempotent(key = "'dispense:' + #prescriptionId", expire = 10, message = "该处方正在发药，请勿重复操作")
    public void dispense(Long prescriptionId, String windowNo) {
        // 状态 CAS：只有 PAID 能发药，并发重复发药会失败
        int cas = mapper.casUpdatePrescriptionStatus(prescriptionId, "PAID", "DISPENSED");
        if (cas == 0) {
            // 可能已发药，二次确认
            PrescriptionView p = mapper.selectPrescription(prescriptionId);
            if (p != null && "DISPENSED".equals(p.getStatus())) {
                throw new BusinessException("该处方已发药，请勿重复发药");
            }
            throw new BusinessException("处方状态不允许发药：" + (p == null ? "不存在" : p.getStatus()));
        }

        PrescriptionView p = mapper.selectPrescription(prescriptionId);
        List<PrescriptionView.Item> items = mapper.listPrescriptionItems(prescriptionId);
        String dispenseNo = "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
        Long pharmacistId = UserContext.getUserId();
        String pharmacistName = UserContext.getUsername();

        for (PrescriptionView.Item item : items) {
            // 乐观锁扣减，最多重试 3 次（版本冲突场景）
            boolean success = false;
            for (int retry = 0; retry < 3 && !success; retry++) {
                DrugStock stock = mapper.selectStockByDrugId(item.getDrugId());
                if (stock == null || BigDecimal.valueOf(stock.getQuantity()).compareTo(item.getQuantity()) < 0) {
                    throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH.getCode(),
                            "药品【" + item.getDrugName() + "】库存不足（剩余 " + (stock == null ? 0 : stock.getQuantity()) + "）");
                }
                int updated = mapper.deductStock(item.getDrugId(), item.getQuantity(), stock.getVersion());
                success = updated > 0;
                if (!success) {
                    try { TimeUnit.MILLISECONDS.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }
            if (!success) {
                throw new BusinessException("药品【" + item.getDrugName() + "】库存扣减冲突，请重试");
            }
            DispenseRecord record = new DispenseRecord();
            record.setDispenseNo(dispenseNo);
            record.setPrescriptionId(prescriptionId);
            record.setPrescriptionNo(p.getPrescriptionNo());
            record.setPatientId(p.getPatientId());
            record.setPatientName(p.getPatientName());
            record.setDrugId(item.getDrugId());
            record.setDrugName(item.getDrugName());
            record.setQuantity(item.getQuantity());
            record.setUnit(item.getUnit());
            record.setPharmacistId(pharmacistId);
            record.setPharmacistName(pharmacistName);
            record.setWindowNo(windowNo);
            mapper.insertDispenseRecord(record);
        }

        log.info("发药完成: 处方{} 药师{} 窗口{} 药品{}种", p.getPrescriptionNo(), pharmacistName, windowNo, items.size());

        // 异步发 Kafka：库存预警检查 + 统计（失败不影响主流程）
        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send("his-dispense-events", dispenseNo,
                        "{\"prescriptionId\":" + prescriptionId + ",\"dispenseNo\":\"" + dispenseNo + "\"}");
            } catch (Exception e) {
                log.warn("Kafka 发药事件发送失败（不影响发药结果）: {}", e.getMessage());
            }
        }
    }

    /** 发药统计：近 N 天每日发药单数/数量 + 发药 TOP10 药品（供 ECharts） */
    public Map<String, Object> stats(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        Date since = cal.getTime();
        Map<String, Object> result = new HashMap<>(4);
        result.put("daily", mapper.statsDaily(since));
        result.put("topDrugs", mapper.statsTopDrugs(since));
        return result;
    }

    /** 药品目录分页 */
    public Map<String, Object> listDrugs(int page, int size) {
        int offset = (page - 1) * size;
        Map<String, Object> data = new HashMap<>(2);
        data.put("list", mapper.listDrugs(size, offset));
        data.put("total", mapper.countDrugs());
        return data;
    }
}
