package com.nac.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nac.auth.entity.IsolationConfig;
import com.nac.auth.entity.IsolationRecord;
import com.nac.auth.mapper.IsolationRecordMapper;
import com.nac.common.exception.BusinessException;
import com.nac.common.coa.CoaProtocol;
import com.nac.common.coa.CoaQueue;
import com.nac.common.isolation.IsolationProtocol;
import com.nac.common.redis.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 隔离区/修复区记录服务：记录查询、人工释放、转入修复区、统计。
 *
 * <p>终端侧进入隔离由 nac-radius 认证链路写入；本服务负责管理端操作与状态收敛。</p>
 */
@Slf4j
@Service
public class IsolationRecordService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IsolationRecordMapper mapper;
    private final IsolationConfigService configService;
    private final RemediationService remediationService;
    private final RedisUtil redis;

    public IsolationRecordService(IsolationRecordMapper mapper, IsolationConfigService configService,
                                  RemediationService remediationService, RedisUtil redis) {
        this.mapper = mapper;
        this.configService = configService;
        this.remediationService = remediationService;
        this.redis = redis;
    }

    public List<IsolationRecord> list(String status, int limit) {
        int lim = Math.min(Math.max(limit, 1), 500);
        String st = status == null ? "" : status.trim().toUpperCase();
        List<IsolationRecord> rows = "ACTIVE".equals(st) || "RELEASED".equals(st)
                ? mapper.selectByStatus(st, lim) : mapper.selectRecent(lim);
        return rows;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        int active = mapper.countActive();
        int quarantine = mapper.countActiveByZone(IsolationProtocol.ZONE_QUARANTINE);
        int remediation = mapper.countActiveByZone(IsolationProtocol.ZONE_REMEDIATION);
        m.put("active", active);
        m.put("quarantine", quarantine);
        m.put("remediation", remediation);
        m.put("released", Math.max(0, mapper.countReleased()));
        return m;
    }

    public IsolationRecord active(String mac) {
        return mapper.selectActiveByMac(IsolationProtocol.normalizeMac(mac));
    }

    /** 人工释放：清 Redis 状态，终端下次认证回到正常 VLAN/ACL。 */
    public void release(Long id, String cause) {
        IsolationRecord r = find(id);
        int n = mapper.release(id, cause == null || cause.isBlank() ? "管理员人工释放" : cause);
        if (n > 0) {
            redis.delete(IsolationProtocol.stateKey(r.getMac()));
            log.info("隔离记录人工释放: id={} mac={}", id, r.getMac());
            // 在线会话需重新认证才能恢复正式 ACL/VLAN
            coa(r.getMac(), CoaProtocol.ACTION_DISCONNECT, "NORMAL", null, null,
                    cause == null || cause.isBlank() ? "管理员人工释放" : cause);
        }
    }

    public void releaseByMac(String mac, String cause) {
        IsolationRecord r = mapper.selectActiveByMac(IsolationProtocol.normalizeMac(mac));
        if (r != null) release(r.getId(), cause);
    }

    /** 转入修复区：更新区域与下发通道，并按缺失项生成修复任务。 */
    public Map<String, Object> enterRemediation(Long id) {
        IsolationRecord r = find(id);
        if (!IsolationProtocol.STATUS_ACTIVE.equalsIgnoreCase(r.getStatus()))
            throw new BusinessException(400, "该记录已释放，无法转入修复区");
        IsolationConfig cfg = configService.get();
        r.setZone(IsolationProtocol.ZONE_REMEDIATION);
        r.setChannelType(cfg.getMode());
        r.setChannelValue(IsolationProtocol.MODE_VLAN.equals(cfg.getMode())
                ? String.valueOf(cfg.getRemediationVlan()) : cfg.getRemediationAcl());
        r.setReason("管理员转入修复区");
        List<String> missing = parseMissing(r.getMissingJson());
        mapper.updateZone(r);
        int created = remediationService.createTasks(r, missing);
        coa(r.getMac(), CoaProtocol.ACTION_AUTH_CHANGE, IsolationProtocol.ZONE_REMEDIATION,
                r.getChannelType(), r.getChannelValue(), "管理员转入修复区");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("record", r);
        out.put("createdTasks", created);
        out.put("tasks", remediationService.tasksByRecord(r.getId()));
        return out;
    }

    /** 投递动态授权命令（由 nac-radius 消费执行；未开启 CoA 时消费者直接丢弃）。 */
    private void coa(String mac, String action, String zone, String channelType,
                     String channelValue, String reason) {
        try {
            CoaQueue.enqueue(redis, new CoaProtocol.Command(IsolationProtocol.normalizeMac(mac),
                    action, zone, channelType, channelValue, reason, System.currentTimeMillis()));
        } catch (Exception e) {
            log.debug("CoA 投递失败(忽略): mac={} {}", mac, e.getMessage());
        }
    }

    private IsolationRecord find(Long id) {
        IsolationRecord r = mapper.selectById(id);
        if (r == null) throw new BusinessException(404, "隔离记录不存在");
        return r;
    }

    private List<String> parseMissing(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
