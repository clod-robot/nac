package com.nac.radius.entity;

import lombok.Data;
import java.time.LocalDateTime;

/** RADIUS 计费历史明细（等保审计留痕，对应 radius_acct_record）。 */
@Data
public class RadiusAcctRecord {
    private Long id;
    private String acctSessionId;
    private String usernameMask;
    private String mac;
    private String nasIp;
    private Integer statusType;      // 1 Start 2 Stop 3 Interim
    private Long sessionTime;
    private Long inputOctets;
    private Long outputOctets;
    private Integer terminateCause;
    private LocalDateTime createTime;
}
