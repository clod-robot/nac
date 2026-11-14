package com.nac.radius.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nac.common.isolation.IsolationProtocol;
import com.nac.common.redis.RedisUtil;
import com.nac.radius.entity.*;
import com.nac.radius.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 隔离区 / 修复区服务（认证链路侧）。
 *
 * <p>职责：隔离配置热读取；认证未通过时写入隔离记录与 Redis 状态；计算下发的 ACL / VLAN；
 * 修复区任务编排（与补丁库、病毒库联动）；合规后自动释放。</p>
 *
 * <p>所有 DB / Redis 操作失败均降级（记录日志并继续认证流程），避免隔离组件故障导致全网阻断。</p>
 */
@Slf4j
@Service
public class IsolationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long CACHE_TTL_MS = 10_000;

    /** 下发通道：type=ACL 时 value 为 Filter-Id 名；type=VLAN 时 value 为 VLAN ID。 */
    public record Channel(String type, String value, int vlan, String acl) {}

    private final IsolationConfigMapper configMapper;
    private final IsolationRecordMapper recordMapper;
    private final RemediationRuleMapper ruleMapper;
    private final PatchLibraryMapper patchMapper;
    private final AvLibraryMapper avMapper;
    private final RemediationTaskMapper taskMapper;
    private final PopupTemplateMapper popupMapper;
    private final RedisUtil redis;

    private volatile IsolationConfig cached;
    private volatile long expireAt;

    public IsolationService(IsolationConfigMapper configMapper, IsolationRecordMapper recordMapper,
                            RemediationRuleMapper ruleMapper, PatchLibraryMapper patchMapper,
                            AvLibraryMapper avMapper, RemediationTaskMapper taskMapper,
                            PopupTemplateMapper popupMapper, RedisUtil redis) {
        this.configMapper = configMapper;
        this.recordMapper = recordMapper;
        this.ruleMapper = ruleMapper;
        this.patchMapper = patchMapper;
        this.avMapper = avMapper;
        this.taskMapper = taskMapper;
        this.popupMapper = popupMapper;
        this.redis = redis;
    }

    // ---------------- 配置 ----------------

    public IsolationConfig config() {
        long now = System.currentTimeMillis();
        IsolationConfig c = cached;
        if (c != null && now < expireAt) return c;
        try {
            c = configMapper.select();
        } catch (Exception e) {
            log.warn("隔离配置读取失败: {}", e.getMessage());
        }
        if (c == null) {
            c = new IsolationConfig();
            c.setId(1L);
            c.setEnabled(0);
            c.setMode(IsolationProtocol.MODE_ACL);
            c.setQuarantineAcl("NAC-QUARANTINE");
            c.setRemediationAcl("NAC-REMEDIATION");
            c.setQuarantineVlan(300);
            c.setRemediationVlan(301);
            c.setAutoRelease(1);
            c.setReleaseHoldMinutes(30);
        }
        cached = c;
        expireAt = now + CACHE_TTL_MS;
        return c;
    }

    public boolean enabled() {
        IsolationConfig c = config();
        return c.getEnabled() != null && c.getEnabled() == 1;
    }

    public boolean autoRelease() {
        IsolationConfig c = config();
        return c.getAutoRelease() == null || c.getAutoRelease() == 1;
    }

    /** 计算某区域应下发的通道。 */
    public Channel channel(String zone) {
        IsolationConfig c = config();
        boolean rem = IsolationProtocol.ZONE_REMEDIATION.equals(zone);
        if (IsolationProtocol.MODE_VLAN.equalsIgnoreCase(c.getMode())) {
            int vlan = rem ? safeInt(c.getRemediationVlan(), 301) : safeInt(c.getQuarantineVlan(), 300);
            return new Channel(IsolationProtocol.MODE_VLAN, String.valueOf(vlan), vlan, null);
        }
        String acl = rem ? c.getRemediationAcl() : c.getQuarantineAcl();
        if (acl == null || acl.isBlank()) acl = rem ? "NAC-REMEDIATION" : "NAC-QUARANTINE";
        return new Channel(IsolationProtocol.MODE_ACL, acl, 0, acl);
    }

    private static int safeInt(Integer v, int def) {
        return v == null || v <= 0 ? def : v;
    }

    // ---------------- 隔离状态 ----------------

    /**
     * 认证未通过时记录隔离（幂等：同一 MAC 已有 ACTIVE 记录则更新区域/通道）。
     *
     * @return 实际下发通道；失败返回 null（调用方应退回拒绝认证）
     */
    public Channel markQuarantine(String mac, String ip, String username, String nasIp,
                                  String reason, List<String> missing) {
        String m = IsolationProtocol.normalizeMac(mac);
        if (m.length() != 12) return null;
        Channel ch = channel(IsolationProtocol.ZONE_QUARANTINE);
        String missingJson = toJson(missing);
        try {
            IsolationRecord r = recordMapper.selectActiveByMac(m);
            if (r == null) {
                r = new IsolationRecord();
                r.setMac(m);
                r.setIp(ip);
                r.setUsername(username);
                r.setNasIp(nasIp);
                r.setZone(IsolationProtocol.ZONE_QUARANTINE);
                r.setStatus(IsolationProtocol.STATUS_ACTIVE);
                r.setReason(truncate(reason, 250));
                r.setMissingJson(missingJson);
                r.setChannelType(ch.type());
                r.setChannelValue(ch.value());
                recordMapper.insert(r);
            } else {
                // 已在隔离区/修复区：保持修复区（避免回退），仅刷新缺失项与检查时间
                if (!IsolationProtocol.ZONE_REMEDIATION.equals(r.getZone())) {
                    r.setZone(IsolationProtocol.ZONE_QUARANTINE);
                    r.setChannelType(ch.type());
                    r.setChannelValue(ch.value());
                } else {
                    Channel rc = channel(IsolationProtocol.ZONE_REMEDIATION);
                    r.setChannelType(rc.type());
                    r.setChannelValue(rc.value());
                    ch = rc;
                }
                r.setMissingJson(missingJson);
                recordMapper.updateZoneAndTouch(r);
            }
            writeState(m, r.getZone(), IsolationProtocol.STATUS_ACTIVE, reason, ch.value());
        } catch (Exception e) {
            log.warn("写隔离记录失败: mac={} {}", m, e.getMessage());
        }
        return ch;
    }

    /** 终端申请进入修复区：切区域、生成修复任务、写 Redis 状态。 */
    public Map<String, Object> enterRemediation(String mac, String ip, String username,
                                                String reason, List<String> missing) {
        String m = IsolationProtocol.normalizeMac(mac);
        Map<String, Object> out = new LinkedHashMap<>();
        if (m.length() != 12) {
            out.put("ok", false);
            out.put("message", "MAC 格式不合法");
            return out;
        }
        Channel ch = channel(IsolationProtocol.ZONE_REMEDIATION);
        IsolationRecord r;
        try {
            r = recordMapper.selectActiveByMac(m);
            if (r == null) {
                r = new IsolationRecord();
                r.setMac(m);
                r.setIp(ip);
                r.setUsername(username);
                r.setZone(IsolationProtocol.ZONE_REMEDIATION);
                r.setStatus(IsolationProtocol.STATUS_ACTIVE);
                r.setReason(truncate(reason == null ? "终端申请进入修复区" : reason, 250));
                r.setMissingJson(toJson(missing));
                r.setChannelType(ch.type());
                r.setChannelValue(ch.value());
                recordMapper.insert(r);
            } else {
                r.setZone(IsolationProtocol.ZONE_REMEDIATION);
                r.setChannelType(ch.type());
                r.setChannelValue(ch.value());
                if (missing != null && !missing.isEmpty()) r.setMissingJson(toJson(missing));
                recordMapper.updateZoneAndTouch(r);
            }
            createTasks(r, missing);
            writeState(m, IsolationProtocol.ZONE_REMEDIATION, IsolationProtocol.STATUS_ACTIVE,
                    r.getReason(), ch.value());
            out.put("ok", true);
            out.put("zone", IsolationProtocol.ZONE_REMEDIATION);
            out.put("channelType", ch.type());
            out.put("channelValue", ch.value());
            out.put("remediationUrl", config().getRemediationPortalUrl());
            out.put("recordId", r.getId());
            out.put("tasks", tasks(m));
        } catch (Exception e) {
            log.warn("进入修复区失败: mac={} {}", m, e.getMessage());
            out.put("ok", false);
            out.put("message", "服务端处理失败");
        }
        return out;
    }

    /** 合规后释放：清 Redis 状态 + 关闭 ACTIVE 记录。 */
    public void release(String mac, String cause) {
        String m = IsolationProtocol.normalizeMac(mac);
        if (m.length() != 12) return;
        try {
            IsolationRecord r = recordMapper.selectActiveByMac(m);
            if (r != null) recordMapper.release(r.getId(), truncate(cause, 250));
            redis.delete(IsolationProtocol.stateKey(m));
            log.info("隔离释放: mac={} 原因={}", m, cause);
        } catch (Exception e) {
            log.warn("隔离释放失败: mac={} {}", m, e.getMessage());
        }
    }

    /**
     * 合规后按配置自动释放：auto_release=1 且已满足最短隔离时长时释放。
     * 未到时长则仅刷新 last_check_time，等待下一轮心跳。
     */
    public void maybeAutoRelease(String mac, String cause) {
        if (!enabled() || !autoRelease()) return;
        String m = IsolationProtocol.normalizeMac(mac);
        if (m.length() != 12) return;
        try {
            IsolationRecord r = recordMapper.selectActiveByMac(m);
            if (r == null) return;
            int hold = config().getReleaseHoldMinutes() == null ? 0 : config().getReleaseHoldMinutes();
            if (r.getEnterTime() != null && hold > 0) {
                long elapsedMin = java.time.Duration.between(r.getEnterTime(),
                        java.time.LocalDateTime.now()).toMinutes();
                if (elapsedMin < hold) {
                    recordMapper.touch(r.getId());
                    return;
                }
            }
            recordMapper.release(r.getId(), truncate(cause, 250));
            redis.delete(IsolationProtocol.stateKey(m));
            log.info("隔离自动释放: mac={} 原因={}", m, cause);
        } catch (Exception e) {
            log.warn("隔离自动释放失败: mac={} {}", m, e.getMessage());
        }
    }

    /**
     * 认证通过（软件基线合规）后关闭残留隔离记录。
     *
     * <p>终端可能因客户端不再上报心跳导致 ACTIVE 记录滞留，管理端一直显示“隔离中”；
     * 认证成功说明终端已恢复，记录只需置为 RELEASED 供追溯，不再影响网络。
     * 仅 auto_release=1 时生效（0 表示由管理员人工释放）。</p>
     */
    public void releaseOnAuthSuccess(String mac) {
        if (!autoRelease()) return;
        String m = IsolationProtocol.normalizeMac(mac);
        if (m.length() != 12) return;
        try {
            IsolationRecord r = recordMapper.selectActiveByMac(m);
            if (r == null || r.getId() == null) return;
            String cause = "认证通过，终端已合规";
            if (recordMapper.release(r.getId(), cause) > 0) {
                redis.delete(IsolationProtocol.stateKey(m));
                log.info("认证通过自动清除残留隔离记录: mac={} record={}", m, r.getId());
            }
        } catch (Exception e) {
            log.warn("认证通过清理隔离记录失败: mac={} {}", m, e.getMessage());
        }
    }

    /**
     * 修复任务全部完成后立即迁回正常办公区（无需人工干预）。
     *
     * <p>与「合规心跳自动释放」互补：该终端本次隔离记录下的修复任务全部为 DONE
     * （无 PENDING / RUNNING / FAILED）时，直接关闭 ACTIVE 记录并清除 Redis 隔离状态，
     * 下一次 RADIUS 认证即按正常 ACL / VLAN 下发。需 isolation.enabled=1 且 auto_release=1；
     * 本路径由明确的修复动作完成触发，不受 release_hold_minutes 最短驻留时长限制。</p>
     *
     * @return 已释放返回释放原因；未满足条件（无记录/任务未完成/存在失败/自动释放关闭）返回 null
     */
    public String releaseIfRemediationDone(String mac) {
        if (!enabled() || !autoRelease()) return null;
        String m = IsolationProtocol.normalizeMac(mac);
        if (m.length() != 12) return null;
        try {
            IsolationRecord r = recordMapper.selectActiveByMac(m);
            if (r == null || r.getId() == null) return null;
            List<RemediationTask> tasks = taskMapper.selectByRecord(r.getId());
            if (tasks.isEmpty()) return null;
            int done = 0;
            for (RemediationTask t : tasks) {
                String st = t.getStatus() == null ? "" : t.getStatus();
                // CANCELLED：被同规则新任务取代的旧任务，不参与判定
                if ("CANCELLED".equalsIgnoreCase(st)) continue;
                if ("DONE".equalsIgnoreCase(st)) {
                    done++;
                    continue;
                }
                return null;
            }
            // 全部任务均被取消（无实际完成的修复动作）不释放
            if (done == 0) return null;
            String cause = "修复任务全部完成";
            // release 的 SQL 带 status='ACTIVE' 条件，多任务并发上报时仅一次生效
            if (recordMapper.release(r.getId(), cause) > 0) {
                redis.delete(IsolationProtocol.stateKey(m));
                log.info("修复任务全部完成，自动迁回正常办公区: mac={} record={} 任务数={}",
                        m, r.getId(), tasks.size());
                return cause;
            }
        } catch (Exception e) {
            log.warn("修复完成自动迁回失败: mac={} {}", m, e.getMessage());
        }
        return null;
    }

    /** 终端隔离状态（Redis 优先，未命中回源 DB）。 */
    public Map<String, Object> state(String mac) {
        String m = IsolationProtocol.normalizeMac(mac);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mac", m);
        out.put("active", false);
        out.put("zone", "");
        out.put("status", "");
        try {
            String json = redis.get(IsolationProtocol.stateKey(m));
            if (json != null && !json.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedState = MAPPER.readValue(json, Map.class);
                out.putAll(cachedState);
                out.put("active", true);
                out.put("remediationUrl", config().getRemediationPortalUrl());
                return out;
            }
            IsolationRecord r = recordMapper.selectActiveByMac(m);
            if (r != null) {
                out.put("active", true);
                out.put("zone", r.getZone());
                out.put("status", r.getStatus());
                out.put("reason", r.getReason());
                out.put("channelType", r.getChannelType());
                out.put("channelValue", r.getChannelValue());
                out.put("enterTime", r.getEnterTime() == null ? null : r.getEnterTime().toString());
                out.put("remediationUrl", config().getRemediationPortalUrl());
            }
        } catch (Exception e) {
            log.warn("读取隔离状态失败: mac={} {}", m, e.getMessage());
        }
        return out;
    }

    private void writeState(String mac, String zone, String status, String reason, String channelValue) {
        try {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("zone", zone);
            s.put("status", status);
            s.put("reason", reason == null ? "" : reason);
            s.put("channelType", channel(zone).type());
            s.put("channelValue", channelValue);
            s.put("ts", System.currentTimeMillis() / 1000);
            redis.set(IsolationProtocol.stateKey(mac), MAPPER.writeValueAsString(s),
                    IsolationProtocol.STATE_TTL);
        } catch (Exception e) {
            log.warn("写隔离状态失败: mac={} {}", mac, e.getMessage());
        }
    }

    // ---------------- 修复任务 ----------------

    /** 当前 ACTIVE 隔离记录下的修复任务（已释放的历史任务不再下发，避免客户端重复执行）。 */
    public List<RemediationTask> tasks(String mac) {
        String m = IsolationProtocol.normalizeMac(mac);
        if (m.length() != 12) return List.of();
        try {
            IsolationRecord r = recordMapper.selectActiveByMac(m);
            if (r == null || r.getId() == null) return List.of();
            return taskMapper.selectByRecord(r.getId());
        } catch (Exception e) {
            log.warn("查询修复任务失败: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean reportTask(Long taskId, String mac, String status, String resultMsg) {
        String m = IsolationProtocol.normalizeMac(mac);
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PENDING", "RUNNING", "DONE", "FAILED").contains(st)) return false;
        try {
            return taskMapper.updateStatus(taskId, m, st, truncate(resultMsg, 480)) > 0;
        } catch (Exception e) {
            log.warn("更新修复任务失败: id={} {}", taskId, e.getMessage());
            return false;
        }
    }

    public int unfinishedTasks(Long recordId) {
        try {
            return taskMapper.countUnfinished(recordId);
        } catch (Exception e) {
            return 0;
        }
    }

    public int failedTasks(Long recordId) {
        try {
            return taskMapper.countFailed(recordId);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 按规则生成修复任务（与补丁库/病毒库联动：同规则在途任务不重复生成，失败任务被新任务取代）。 */
    public int createTasks(IsolationRecord record, List<String> missing) {
        if (record == null || record.getId() == null) return 0;
        try {
            List<RemediationRule> rules = ruleMapper.selectEnabled();
            List<RemediationTask> exists = taskMapper.selectByRecord(record.getId());
            int created = 0;
            for (RemediationRule r : rules) {
                if (!matches(r, missing)) continue;
                RemediationTask t = build(record, r);
                if (t == null) continue;
                boolean inFlight = exists.stream().anyMatch(e -> r.getId() != null && r.getId().equals(e.getRuleId())
                        && ("PENDING".equalsIgnoreCase(e.getStatus()) || "RUNNING".equalsIgnoreCase(e.getStatus())));
                if (inFlight) continue;
                // 旧任务（FAILED 等）标记为 CANCELLED，防止历史失败永久阻塞“全部完成”判定
                taskMapper.cancelUnfinishedByRule(record.getId(), r.getId(), "已被新任务取代");
                taskMapper.insert(t);
                exists.add(t);
                created++;
            }
            return created;
        } catch (Exception e) {
            log.warn("生成修复任务失败: record={} {}", record.getId(), e.getMessage());
            return 0;
        }
    }

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

    // ---------------- 弹窗 ----------------

    /** 弹窗解析：策略级覆盖 > 全局模板；正文 {software} 替换为缺失项。 */
    public Map<String, Object> popup(List<String> missing, List<SoftwarePolicy> policies) {
        String software = missing == null || missing.isEmpty() ? "指定安全软件" : String.join("、", missing);
        String title = null, content = null, actionText = null, actionUrl = null;
        if (policies != null) {
            for (SoftwarePolicy p : policies) {
                if (p.getRequired() == null || p.getRequired() != 1) continue;
                if (title == null) title = p.getPopupTitle();
                if (content == null) content = p.getPopupContent();
                if (actionText == null) actionText = p.getPopupActionText();
                if (actionUrl == null) actionUrl = p.getPopupActionUrl();
            }
        }
        Integer delaySec = null;
        Integer allowDelay = null;
        try {
            PopupTemplate t = popupMapper.select();
            if (t != null) {
                if (title == null) title = t.getTitle();
                if (content == null) content = t.getContent();
                if (actionText == null) actionText = t.getActionText();
                if (actionUrl == null) actionUrl = t.getActionUrl();
                delaySec = t.getDelaySec();
                allowDelay = t.getAllowDelay();
            }
        } catch (Exception e) {
            log.warn("弹窗模板读取失败: {}", e.getMessage());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title == null ? "终端安全合规提醒" : title);
        m.put("content", (content == null ? "检测到您的终端未运行必需的安全软件：{software}" : content)
                .replace("{software}", software));
        m.put("actionText", actionText == null ? "立即处理" : actionText);
        m.put("actionUrl", actionUrl == null ? "" : actionUrl);
        m.put("delaySec", delaySec == null ? 30 : delaySec);
        m.put("allowDelay", allowDelay == null || allowDelay == 1);
        return m;
    }

    private String toJson(List<String> list) {
        try {
            return MAPPER.writeValueAsString(list == null ? new ArrayList<>() : list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
