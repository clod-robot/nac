package com.nac.auth.dto;

import lombok.Data;

/** 网络配置修改请求。仅提交需要变更的字段；留空则该项不处理。 */
@Data
public class NetApplyReq {
    private String name;        // 必填，接口名
    private Integer mtu;
    private Boolean up;         // 启用/禁用
    private String ipv4Address;
    private Integer ipv4Prefix;
    private String ipv6Address;
    private Integer ipv6Prefix;
    private String gateway;
    private String dns1;
    private String dns2;
}
