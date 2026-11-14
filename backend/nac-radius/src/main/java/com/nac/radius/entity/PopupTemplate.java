package com.nac.radius.entity;

import lombok.Data;

/** 终端合规弹窗全局模板（只读，单行 id=1）。 */
@Data
public class PopupTemplate {
    private Long id;
    private String title;
    private String content;
    private String actionText;
    private String actionUrl;
    private Integer delaySec;
    private Integer allowDelay;
    private Integer enabled;
}
