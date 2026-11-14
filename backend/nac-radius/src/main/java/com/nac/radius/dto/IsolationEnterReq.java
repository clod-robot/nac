package com.nac.radius.dto;

import lombok.Data;

import java.util.List;

/** 终端申请进入修复区。 */
@Data
public class IsolationEnterReq {
    private String mac;
    private String ip;
    private String username;
    private String reason;
    private List<String> missing;
}
