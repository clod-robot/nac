package com.nac.radius.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 病毒库（只读）。 */
@Data
public class AvLibrary {
    private Long id;
    private String name;
    private String vendor;
    private String version;
    private String sha256;
    private String fileUrl;
    private String installCmd;
    private String silentArgs;
    private String description;
    private Integer enabled;
    private LocalDateTime publishTime;
}
