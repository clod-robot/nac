package com.nac.radius.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 补丁库（只读）。 */
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
}
