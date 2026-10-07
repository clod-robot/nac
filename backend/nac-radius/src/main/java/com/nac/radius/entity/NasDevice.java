package com.nac.radius.entity;

import lombok.Data;
import java.util.Date;

/** NAS 设备台账（RADIUS 认证来源设备自动登记）。 */
@Data
public class NasDevice {
    private Long id;
    private String nasIp;
    private String nasName;
    private String nasIdentifier;
    private String lastUser;
    private Long successCount;
    private Long failCount;
    private Date lastSeen;
    private Date createTime;
}
