package com.nac.radius.controller;

import com.nac.common.isolation.IsolationProtocol;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.software.SoftwareBaseline;
import com.nac.radius.dto.IsolationEnterReq;
import com.nac.radius.dto.RemediationReportReq;
import com.nac.radius.entity.IsolationConfig;
import com.nac.radius.entity.RemediationTask;
import com.nac.radius.service.IsolationService;
import com.nac.radius.service.SoftwareBaselineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 终端隔离区 / 修复区接口（终端直连 8083）。
 *
 * <p>终端心跳不合规后：查询隔离状态 → 展示弹窗 → 可申请进入修复区 → 拉取修复任务（补丁/病毒库）
 * → 上报任务结果；修复任务全部完成（或软件基线恢复合规）后按配置自动迁回正常办公区。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/client")
public class ClientIsolationController {

    private final IsolationService isolationService;
    private final SoftwareBaselineService softwareBaseline;

    public ClientIsolationController(IsolationService isolationService,
                                     SoftwareBaselineService softwareBaseline) {
        this.isolationService = isolationService;
        this.softwareBaseline = softwareBaseline;
    }

    /** 当前隔离/修复区状态（含下发通道与修复门户地址）。 */
    @GetMapping("/isolation/state")
    @RateLimit(limit = 120, window = 60)
    public Result<Map<String, Object>> state(@RequestParam String mac) {
        return Result.success(isolationService.state(mac));
    }

    /** 隔离/修复配置摘要（客户端展示下发通道，不含敏感信息）。 */
    @GetMapping("/isolation/config")
    @RateLimit(limit = 60, window = 60)
    public Result<Map<String, Object>> config() {
        IsolationConfig c = isolationService.config();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", c.getEnabled());
        m.put("mode", c.getMode());
        m.put("remediationUrl", c.getRemediationPortalUrl());
        m.put("serverIps", c.getRemediationServerIps());
        return Result.success(m);
    }

    /** 申请进入修复区：切区域、生成修复任务。 */
    @PostMapping("/remediation/enter")
    @RateLimit(limit = 60, window = 60)
    public Result<Map<String, Object>> enter(@RequestBody IsolationEnterReq req) {
        if (req == null || req.getMac() == null) return Result.fail(400, "mac 不能为空");
        return Result.success(isolationService.enterRemediation(req.getMac(), req.getIp(),
                req.getUsername(), req.getReason(), req.getMissing()));
    }

    /** 拉取该终端修复任务（含补丁/病毒库下载地址与安装命令）。 */
    @GetMapping("/remediation/tasks")
    @RateLimit(limit = 120, window = 60)
    public Result<List<RemediationTask>> tasks(@RequestParam String mac) {
        return Result.success(isolationService.tasks(mac));
    }

    /** 上报修复任务执行结果；全部 DONE 后立即自动迁回正常办公区（auto_release=1 时）。 */
    @PostMapping("/remediation/report")
    @RateLimit(limit = 120, window = 60)
    public Result<Map<String, Object>> report(@RequestBody RemediationReportReq req) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (req == null || req.getTaskId() == null || req.getMac() == null) {
            out.put("ok", false);
            out.put("message", "taskId/mac 不能为空");
            return Result.success(out);
        }
        // 可选签名（与软件基线心跳同协议）：携带则校验，缺省兼容旧客户端
        String signErr = verifyReportSign(req);
        if (signErr != null) {
            out.put("ok", false);
            out.put("message", signErr);
            return Result.success(out);
        }
        boolean ok = isolationService.reportTask(req.getTaskId(), req.getMac(), req.getStatus(), req.getResultMsg());
        String released = null;
        if (ok && "DONE".equalsIgnoreCase(req.getStatus())) {
            released = isolationService.releaseIfRemediationDone(req.getMac());
        }
        out.put("ok", ok);
        out.put("released", released != null);
        if (released != null) {
            out.put("zone", "NORMAL");
            out.put("releaseCause", released);
            out.put("message", "修复任务已全部完成，已自动迁回正常办公区");
        } else {
            out.put("message", ok ? "已更新" : "任务不存在或状态非法");
        }
        return Result.success(out);
    }

    /**
     * 校验修复任务上报签名（可选）。
     * payload = nonce|mac|ts|taskId|status，HMAC-SHA256 与软件基线心跳共用共享密钥。
     *
     * @return 校验失败原因；通过或未携带签名返回 null
     */
    private String verifyReportSign(RemediationReportReq req) {
        String sig = req.getSignature();
        if (sig == null || sig.isBlank()) return null;
        String nonce = req.getNonce() == null ? "" : req.getNonce().trim();
        if (nonce.length() < 16) return "签名 nonce 缺失或非法";
        Long ts = req.getTs();
        if (ts == null || Math.abs(System.currentTimeMillis() / 1000 - ts) > 300) return "签名时间戳超出允许窗口";
        String status = req.getStatus() == null ? "" : req.getStatus().trim().toUpperCase();
        String payload = nonce + "|" + SoftwareBaseline.normMac(req.getMac()) + "|" + ts + "|"
                + req.getTaskId() + "|" + status;
        if (!softwareBaseline.verifyApiSign(payload, sig)) return "签名校验失败";
        return null;
    }
}
