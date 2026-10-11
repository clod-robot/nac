package com.nac.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件型 KEK 提供者（Strategy）：从权限受控文件读取主密钥，适合无 KMS 的本地部署。
 * 仅当配置 nac.security.kek.file 指向存在的文件时生效，priority=50。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nac.security.kek.file")
public class FileKekProvider implements KekProvider {

    @Value("${nac.security.kek.file:}")
    private String file;

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public String getKekMaterial() {
        if (file == null || file.isBlank()) {
            return null;
        }
        try {
            Path p = Path.of(file);
            if (!Files.isReadable(p)) {
                log.warn("KEK 文件不可读: {}", file);
                return null;
            }
            return Files.readString(p, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.warn("读取 KEK 文件失败: {}", e.getMessage());
            return null;
        }
    }
}
