package com.nac.auth.service;

import com.nac.auth.entity.*;
import com.nac.auth.mapper.*;
import com.nac.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 修复编排服务：补丁库 / 病毒库 / 修复规则维护，以及按规则为隔离终端生成修复任务。
 *
 * <p>修复区与补丁库、病毒库联动：终端进入修复区后，服务端把命中的补丁/病毒库升级包
 * 作为任务下发（文件地址 + 安装命令），客户端可据此拉取并执行。</p>
 */
@Slf4j
@Service
public class RemediationService {

    private final PatchLibraryMapper patchMapper;
    private final AvLibraryMapper avMapper;
    private final RemediationRuleMapper ruleMapper;
    private final RemediationTaskMapper taskMapper;

    public RemediationService(PatchLibraryMapper patchMapper, AvLibraryMapper avMapper,
                              RemediationRuleMapper ruleMapper, RemediationTaskMapper taskMapper) {
        this.patchMapper = patchMapper;
        this.avMapper = avMapper;
        this.ruleMapper = ruleMapper;
        this.taskMapper = taskMapper;
    }

    // ---------------- 补丁库 / 病毒库 ----------------

    public List<PatchLibrary> patches() { return patchMapper.selectAll(); }

    public void savePatch(PatchLibrary p) {
        if (p.getName() == null || p.getName().isBlank()) throw new BusinessException(400, "补丁名称不能为空");
        if (p.getOsType() == null || p.getOsType().isBlank()) p.setOsType("WINDOWS");
        if (p.getEnabled() == null) p.setEnabled(1);
        if (p.getSha256() != null && !p.getSha256().isBlank()
                && !p.getSha256().trim().toLowerCase(Locale.ROOT).matches("[0-9a-f]{64}"))
            throw new BusinessException(400, "SHA256 需为 64 位十六进制");
        if (p.getId() == null) patchMapper.insert(p);
        else patchMapper.update(p);
    }

    public void deletePatch(Long id) { patchMapper.deleteById(id); }

    public List<AvLibrary> avs() { return avMapper.selectAll(); }

    public void saveAv(AvLibrary a) {
        if (a.getName() == null || a.getName().isBlank()) throw new BusinessException(400, "病毒库名称不能为空");
        if (a.getEnabled() == null) a.setEnabled(1);
        if (a.getSha256() != null && !a.getSha256().isBlank()
                && !a.getSha256().trim().toLowerCase(Locale.ROOT).matches("[0-9a-f]{64}"))
            throw new BusinessException(400, "SHA256 需为 64 位十六进制");
        if (a.getId() == null) avMapper.insert(a);
        else avMapper.update(a);
    }

    public void deleteAv(Long id) { avMapper.deleteById(id); }

    // ---------------- 修复规则 ----------------

    public List<RemediationRule> rules() { return ruleMapper.selectAll(); }

    public void saveRule(RemediationRule r) {
        if (r.getName() == null || r.getName().isBlank()) throw new BusinessException(400, "规则名称不能为空");
        String tt = r.getTriggerType() == null ? "" : r.getTriggerType().trim().toUpperCase(Locale.ROOT);
        if (!List.of("SOFTWARE_MISSING", "AV_OUTDATED", "PATCH_MISSING").contains(tt))
            throw new BusinessException(400, "触发类型不合法");
        r.setTriggerType(tt);
        String at = r.getActionType() == null ? "" : r.getActionType().trim().toUpperCase(Locale.ROOT);
        if (!List.of("INSTALL_PATCH", "UPDATE_AV", "OPEN_URL").contains(at))
            throw new BusinessException(400, "动作类型不合法");
        r.setActionType(at);
        if (!"OPEN_URL".equals(at) && r.getTargetId() == null)
            throw new BusinessException(400, "请选择关联的补丁/病毒库");
        if ("OPEN_URL".equals(at) && (r.getActionUrl() == null || r.getActionUrl().isBlank()))
            throw new BusinessException(400, "OPEN_URL 动作需填写地址");
        if (r.getEnabled() == null) r.setEnabled(1);
        if (r.getId() == null) ruleMapper.insert(r);
        else ruleMapper.update(r);
    }

    public void deleteRule(Long id) { ruleMapper.deleteById(id); }

    // ---------------- 修复任务 ----------------

    public List<RemediationTask> tasksByMac(String mac) { return taskMapper.selectByMac(mac); }

    public List<RemediationTask> tasksByRecord(Long recordId) { return taskMapper.selectByRecord(recordId); }

    public int unfinished(Long recordId) { return taskMapper.countUnfinished(recordId); }

    /**
     * 按启用规则生成修复任务（幂等：同一记录下同类同目标的未完成任务不重复创建）。
     *
     * @param record  隔离记录
     * @param missing 缺失项显示名（软件名/补丁名/病毒库名）
     */
    public int createTasks(IsolationRecord record, List<String> missing) {
        if (record == null || record.getId() == null) return 0;
        List<RemediationRule> rules = ruleMapper.selectEnabled();
        List<RemediationTask> exists = taskMapper.selectByRecord(record.getId());
        int created = 0;
        for (RemediationRule r : rules) {
            if (!matches(r, missing)) continue;
            RemediationTask t = build(record, r);
            if (t == null) continue;
            boolean dup = exists.stream().anyMatch(e -> r.getId() != null && r.getId().equals(e.getRuleId())
                    && !"DONE".equalsIgnoreCase(e.getStatus()) && !"FAILED".equalsIgnoreCase(e.getStatus()));
            if (dup) continue;
            taskMapper.insert(t);
            exists.add(t);
            created++;
        }
        if (created > 0) log.info("生成修复任务: mac={} record={} 数量={}", record.getMac(), record.getId(), created);
        return created;
    }

    /** 规则是否命中：触发值为空=任意；否则缺失项包含该值（忽略大小写）。 */
    private boolean matches(RemediationRule r, List<String> missing) {
        String v = r.getTriggerValue();
        if (v == null || v.isBlank()) return true;
        if (missing == null || missing.isEmpty()) return false;
        String needle = v.trim().toLowerCase(Locale.ROOT);
        for (String m : missing) {
            if (m != null && m.toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }

    private RemediationTask build(IsolationRecord record, RemediationRule r) {
        RemediationTask t = new RemediationTask();
        t.setRecordId(record.getId());
        t.setMac(record.getMac());
        t.setRuleId(r.getId());
        t.setActionType(r.getActionType());
        t.setStatus("PENDING");
        if ("INSTALL_PATCH".equals(r.getActionType())) {
            PatchLibrary p = patchMapper.selectById(r.getTargetId());
            if (p == null || !Integer.valueOf(1).equals(p.getEnabled())) return null;
            t.setLibType("PATCH");
            t.setLibId(p.getId());
            t.setTitle("安装补丁：" + p.getName() + (p.getVersion() == null ? "" : " (" + p.getVersion() + ")"));
            t.setFileUrl(p.getFileUrl());
            t.setInstallCmd(p.getInstallCmd());
        } else if ("UPDATE_AV".equals(r.getActionType())) {
            AvLibrary a = avMapper.selectById(r.getTargetId());
            if (a == null || !Integer.valueOf(1).equals(a.getEnabled())) return null;
            t.setLibType("AV");
            t.setLibId(a.getId());
            t.setTitle("更新病毒库：" + a.getName() + (a.getVersion() == null ? "" : " " + a.getVersion()));
            t.setFileUrl(a.getFileUrl());
            t.setInstallCmd(a.getInstallCmd());
        } else {
            t.setLibType("URL");
            t.setTitle("打开修复指引：" + r.getName());
            t.setFileUrl(r.getActionUrl());
        }
        return t;
    }
}
