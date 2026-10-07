package com.nac.auth.dto;

import lombok.Data;

/** 网络接口信息（读）。 */
@Data
public class NetInterface {
    private String name;
    private boolean up;
    private String operState;
    /** 协商速率 Mbps，未知为 0 */
    private long speedMbps;
    private int mtu;
    private IpInfo ipv4;
    private IpInfo ipv6;
    private String gateway;
    private String dns1;
    private String dns2;

    @Data
    public static class IpInfo {
        private String address;
        private Integer prefix;
        /** IPv4 点分掩码（仅 v4 有） */
        private String mask;
    }
}
