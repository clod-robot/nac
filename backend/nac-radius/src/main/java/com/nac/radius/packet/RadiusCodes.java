package com.nac.radius.packet;

/**
 * RADIUS 协议常量（RFC 2865 / 2866）。
 */
public final class RadiusCodes {

    private RadiusCodes() {}

    /** 报文 Code */
    public static final byte ACCESS_REQUEST = 1;
    public static final byte ACCESS_ACCEPT = 2;
    public static final byte ACCESS_REJECT = 3;
    public static final byte ACCOUNTING_REQUEST = 4;
    public static final byte ACCOUNTING_RESPONSE = 5;
    public static final byte ACCESS_CHALLENGE = 11;

    /** 常用属性 Type */
    public static final int USER_NAME = 1;
    public static final int USER_PASSWORD = 2;
    public static final int CHAP_PASSWORD = 3;
    public static final int NAS_IP_ADDRESS = 4;
    public static final int NAS_PORT = 5;
    public static final int FRAMED_IP_ADDRESS = 8;
    public static final int CALLED_STATION_ID = 30;
    public static final int CALLING_STATION_ID = 31; // 通常为终端 MAC
    public static final int ACCT_STATUS_TYPE = 40;
    public static final int ACCT_DELAY_TIME = 41;
    public static final int ACCT_INPUT_OCTETS = 42;
    public static final int ACCT_OUTPUT_OCTETS = 43;
    public static final int ACCT_SESSION_ID = 44;
    public static final int ACCT_AUTHENTIC = 45;
    public static final int ACCT_SESSION_TIME = 46;
    public static final int ACCT_INPUT_PACKETS = 47;
    public static final int ACCT_OUTPUT_PACKETS = 48;
    public static final int ACCT_TERMINATE_CAUSE = 49;
    public static final int NAS_IDENTIFIER = 32;
    public static final int FRAMED_MTU = 12;

    /** 计费 Acct-Status-Type 取值 */
    public static final int ACCT_START = 1;
    public static final int ACCT_STOP = 2;
    public static final int ACCT_INTERIM = 3;

    /** 认证响应属性 */
    public static final int REPLY_MESSAGE = 18;
    public static final int SESSION_TIMEOUT = 27;
    public static final int STATE = 24; // EAP 挑战态，NAS 原样回传
    public static final int EAP_MESSAGE = 79; // 802.1X EAP 载荷
    public static final int MESSAGE_AUTHENTICATOR = 80; // EAP 报文完整性（HMAC-MD5）
    public static final int TUNNEL_TYPE = 64;
    public static final int TUNNEL_MEDIUM_TYPE = 65;
    public static final int TUNNEL_PRIVATE_GROUP_ID = 81; // VLAN

    /** EAP 协议（RFC 3748） */
    public static final int EAP_REQUEST = 1;
    public static final int EAP_RESPONSE = 2;
    public static final int EAP_SUCCESS = 3;
    public static final int EAP_FAILURE = 4;
    public static final int EAP_TYPE_IDENTITY = 1;
    public static final int EAP_TYPE_MD5 = 4;
    public static final int EAP_TYPE_TLS = 13;   // EAP-TLS (RFC 5216)
    public static final int EAP_TYPE_PEAP = 25;  // PEAP (RFC 5216 隧道)

    public static final int HEADER_LEN = 20; // code(1)+id(1)+length(2)+auth(16)
}
