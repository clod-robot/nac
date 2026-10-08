package com.nac.auth.dto;

import lombok.Data;

/** 网络接口信息（读）。 */
@Data
public class NetInterface {
    private String name;
    private boolean up;          // 管理状态（flags 含 UP）
    private boolean linkUp;      // 链路状态（operstate == UP，有载波）
    private String operState;
    /** 协商速率 Mbps，未知为 0 */
    private long speedMbps;
    private int mtu;
    private IpInfo ipv4;
    private IpInfo ipv6;
    /** 默认网关（IPv4，兼容编辑表单） */
    private String gateway;
    /** 按协议族区分的网关，一一对应；无则为 null */
    private String ipv4Gateway;
    private String ipv6Gateway;
    private String dns1;
    private String dns2;
    private String ipv6Dns1;
    private String ipv6Dns2;

    @Data
    public static class IpInfo {
        private String address;
        private Integer prefix;
        /** IPv4 点分掩码（仅 v4 有） */
        private String mask;
    }
}
