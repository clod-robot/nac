package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 补丁库（sys_patch_lib）。 */
@Data
public class PatchLibrary {
    private Long id;
    private String name;
    private String version;
    private String osType;
    private String severity;
    private String sha256;
    private String fileUrl;
    private String installCmd;
    private String silentArgs;
    private String description;
    private Integer enabled;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
