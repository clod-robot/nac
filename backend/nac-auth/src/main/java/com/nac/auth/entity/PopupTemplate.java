package com.nac.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 终端合规弹窗全局模板（sys_popup_template，单行 id=1）。 */
@Data
public class PopupTemplate {
    private Long id;
    private String title;
    private String content;
    private String actionText;
    private String actionUrl;
    /** 允许暂缓的秒数（0=不允许暂缓） */
    private Integer delaySec;
    private Integer allowDelay;
    private Integer enabled;
    private LocalDateTime updateTime;
}
