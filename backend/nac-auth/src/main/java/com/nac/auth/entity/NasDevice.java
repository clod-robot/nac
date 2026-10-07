package com.nac.auth.entity;

import lombok.Data;
import java.util.Date;

/** NAS 设备台账（与 nac-radius 共享 sys_nas 表，此处仅查询/改名）。 */
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
