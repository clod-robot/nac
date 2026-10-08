package com.nac.radius.controller;

import com.nac.radius.eap.CertManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导出 NAC 自签 CA 证书，供 802.1X 终端安装信任（EAP-TLS/PEAP 校验服务端证书）。
 */
@RestController
@RequestMapping("/radius")
public class CaCertController {

    private final CertManager certManager;

    public CaCertController(CertManager certManager) {
        this.certManager = certManager;
    }

    @GetMapping(value = "/ca.pem", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> caPem() {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=nac-ca.pem")
                .body(certManager.getCaCertPem());
    }
}
