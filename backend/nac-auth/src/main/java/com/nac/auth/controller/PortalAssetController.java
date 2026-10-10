package com.nac.auth.controller;

import com.nac.common.exception.BusinessException;
import com.nac.common.log.OperationLog;
import com.nac.common.ratelimit.RateLimit;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * Portal 页面定制资源（LOGO / 轮播图 / 背景图）：
 * - POST /api/portal/admin/upload  管理员上传图片：白名单扩展名 + 魔数校验 + 大小上限 + 随机文件名，防路径穿越
 * - GET  /api/portal/asset/{name}  公开读取（captive portal 页面加载），强校验文件名防穿越
 *
 * 安全要点（等保合规）：
 *  1) 扩展名白名单 png/jpg/jpeg，且校验文件魔数，防止伪装脚本上传；
 *  2) 文件名服务端随机生成（UUID），丢弃用户原始文件名，杜绝路径穿越；
 *  3) 读取前校验规范化路径仍位于资源目录内；
 *  4) 响应携带 X-Content-Type-Options: nosniff，防 MIME 嗅探。
 */
@Slf4j
@RestController
@RequestMapping("/api/portal")
public class PortalAssetController {

    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg");
    private static final long MAX_BYTES = 2L * 1024 * 1024; // 单文件 2MB 上限
    private static final String NAME_PATTERN = "^[A-Za-z0-9]+\\.(png|jpg|jpeg)$";

    @Value("${nac.portal.asset-dir:${user.home}/portal-assets}")
    private String assetDir;

    private Path baseDir() {
        Path p = Paths.get(assetDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new BusinessException(500, "Portal 资源目录不可用");
        }
        return p;
    }

    @PostMapping(value = "/admin/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole("admin")
    @RateLimit(limit = 30, window = 60)
    @OperationLog("上传 Portal 页面资源")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "文件不能为空");
        if (file.getSize() > MAX_BYTES) throw new BusinessException(400, "文件过大，单张最大 2MB");

        String ext = extOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) throw new BusinessException(400, "仅支持 png / jpg / jpeg 图片");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(400, "读取文件失败");
        }
        // 魔数校验：内容必须与扩展名一致，拦截伪装为图片的脚本
        if (!magicOk(ext, bytes)) throw new BusinessException(400, "文件内容与格式不符，已拦截");

        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path base = baseDir();
        Path target = base.resolve(name).normalize();
        if (!target.startsWith(base)) throw new BusinessException(400, "非法文件名");

        try {
            Files.write(target, bytes);
        } catch (IOException e) {
            log.error("保存 Portal 资源失败: {}", e.getMessage());
            throw new BusinessException(500, "保存失败");
        }
        return Result.success("/api/portal/asset/" + name);
    }

    @GetMapping("/asset/{name}")
    public ResponseEntity<Resource> asset(@PathVariable("name") String name) {
        if (name == null || !name.matches(NAME_PATTERN)) {
            return ResponseEntity.badRequest().build();
        }
        Path base = baseDir();
        Path target = base.resolve(name).normalize();
        if (!target.startsWith(base) || !Files.isRegularFile(target) || !Files.isReadable(target)) {
            return ResponseEntity.notFound().build();
        }
        MediaType mt = name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(mt)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(target));
    }

    private static String extOf(String fn) {
        if (fn == null) return "";
        int i = fn.lastIndexOf('.');
        return i < 0 ? "" : fn.substring(i + 1).toLowerCase();
    }

    private static boolean magicOk(String ext, byte[] b) {
        if (b.length < 4) return false;
        if ("png".equals(ext)) {
            return (b[0] & 0xFF) == 0x89 && (b[1] & 0xFF) == 0x50
                    && (b[2] & 0xFF) == 0x4E && (b[3] & 0xFF) == 0x47;
        }
        // jpg / jpeg：FF D8 FF
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }
}
