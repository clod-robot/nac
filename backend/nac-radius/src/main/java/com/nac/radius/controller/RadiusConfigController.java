package com.nac.radius.controller;

import com.nac.common.exception.BusinessException;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import com.nac.radius.eap.CertManager;
import com.nac.radius.service.RadiusConfigService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RADIUS 参数配置（管理端，需 admin）：EAP 分片大小、802.1X CA 证书导入。
 * 均为热生效，无需重启服务。
 */
@RestController
@RequestMapping("/api/radius/config")
public class RadiusConfigController {

    private final RadiusConfigService configService;
    private final CertManager certManager;

    public RadiusConfigController(RadiusConfigService configService, CertManager certManager) {
        this.configService = configService;
        this.certManager = certManager;
    }

    /** 读取当前配置：分片大小（当前/默认/区间/建议）+ CA/服务端证书信息。 */
    @GetMapping
    @RequireRole
    @RateLimit(limit = 120, window = 60)
    public Result<Map<String, Object>> get() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> frag = new LinkedHashMap<>();
        frag.put("current", configService.fragmentSize());
        frag.put("default", RadiusConfigService.FRAG_DEFAULT);
        frag.put("min", RadiusConfigService.FRAG_MIN);
        frag.put("max", RadiusConfigService.FRAG_MAX);
        frag.put("recommend", RadiusConfigService.FRAG_RECOMMEND);
        m.put("fragment", frag);
        m.put("ca", certManager.certInfo());
        return Result.success(m);
    }

    /** 修改 EAP 分片大小（校验 128~1400），热生效。 */
    @PutMapping("/fragment")
    @RequireRole
    @RateLimit(limit = 30, window = 60)
    @OperationLog("修改 EAP 分片大小")
    public Result<Void> setFragment(@RequestBody Map<String, Object> body) {
        Object v = body.get("size");
        if (!(v instanceof Number)) throw new BusinessException(400, "size 必须为数字");
        configService.setFragmentSize(((Number) v).intValue());
        return Result.success();
    }

    /** 导入客户 CA 证书 + 私钥（PEM）。服务端校验可用性，失败则回退不覆盖。 */
    @PostMapping(value = "/ca", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole
    @RateLimit(limit = 10, window = 60)
    @OperationLog("导入 802.1X CA 证书")
    public Result<CertManager.CertInfo> uploadCa(@RequestParam("caCert") MultipartFile caCert,
                                                 @RequestParam("caKey") MultipartFile caKey) {
        String cert = read(caCert, "CA 证书");
        String key = read(caKey, "CA 私钥");
        return Result.success(certManager.applyCustomerCa(cert, key));
    }

    /** 下载当前 CA 证书 PEM（供终端安装信任）。 */
    @GetMapping(value = "/ca.pem", produces = MediaType.TEXT_PLAIN_VALUE)
    @RequireRole
    @RateLimit(limit = 60, window = 60)
    public org.springframework.http.ResponseEntity<String> downloadCa() {
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=nac-ca.pem")
                .body(certManager.getCaCertPem());
    }

    private static String read(MultipartFile f, String name) {
        if (f == null || f.isEmpty()) throw new BusinessException(400, name + "文件不能为空");
        if (f.getSize() > 64 * 1024) throw new BusinessException(400, name + "文件过大");
        try {
            return new String(f.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(400, "读取" + name + "失败");
        }
    }
}
