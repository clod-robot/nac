package com.nac.radius.packet;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * CMCC Portal v2.0 协议报文。
 *
 * 头部（16 字节）：
 *   Version(1) Type(1) AuthType(1) Reserved(2) SerialNo(2) ReqId(1)
 *   UserIp(4) UserPort(2) ErrCode(1) AttrNum(1)
 * 随后是 AttrNum 个属性（TLV：AttrType(1) AttrLen(1) AttrValue），
 * CHAP 认证时尾部追加 16 字节 ChapAuth（MD5）。
 */
public class PortalPacket {

    public static final byte VERSION_1 = 0x01;
    public static final byte VERSION_2 = 0x02;
    /** 默认/兼容回包用 v2，但若由请求构造则沿用请求版本 */
    public static final byte VERSION = 0x02;

    public static final byte TYPE_REQ_CHALLENGE = 1;
    public static final byte TYPE_ACK_CHALLENGE = 2;
    public static final byte TYPE_REQ_AUTH = 3;
    public static final byte TYPE_ACK_AUTH = 4;
    public static final byte TYPE_REQ_LOGOUT = 5;
    public static final byte TYPE_ACK_LOGOUT = 6;
    public static final byte TYPE_AFF_ACK_AUTH = 7;
    public static final byte TYPE_NTF_LOGOUT = 8;
    public static final byte TYPE_REQ_INFO = 9;
    public static final byte TYPE_ACK_INFO = 10;

    public static final byte AUTH_PAP = 0;
    public static final byte AUTH_CHAP = 1;

    // 属性类型
    public static final byte ATTR_USER_NAME = 1;
    public static final byte ATTR_PASSWORD = 2;   // PAP
    public static final byte ATTR_CHALLENGE = 3;
    public static final byte ATTR_CHAP_PASSWORD = 4; // 兼容某些实现
    public static final byte ATTR_TEXT_INFO = 5;

    private byte version;
    private byte type;
    private byte authType;
    private int serialNo;   // 0..65535
    private byte reqId;
    private byte[] userIp = new byte[4];
    private int userPort;
    private byte errCode;
    private final Map<Byte, byte[]> attrs = new HashMap<>();
    private byte[] chapAuth; // 16 bytes, CHAP 时存在

    public byte getVersion() { return version; }
    public void setVersion(byte version) { this.version = version; }
    public byte getType() { return type; }
    public void setType(byte type) { this.type = type; }
    public byte getAuthType() { return authType; }
    public void setAuthType(byte authType) { this.authType = authType; }
    public int getSerialNo() { return serialNo; }
    public void setSerialNo(int serialNo) { this.serialNo = serialNo; }
    public byte getReqId() { return reqId; }
    public void setReqId(byte reqId) { this.reqId = reqId; }
    public byte[] getUserIpRaw() { return userIp; }
    public void setUserIpRaw(byte[] userIp) { this.userIp = userIp; }
    public int getUserPort() { return userPort; }
    public void setUserPort(int userPort) { this.userPort = userPort; }
    public byte getErrCode() { return errCode; }
    public void setErrCode(byte errCode) { this.errCode = errCode; }
    public Map<Byte, byte[]> getAttrs() { return attrs; }
    public byte[] getChapAuth() { return chapAuth; }
    public void setChapAuth(byte[] chapAuth) { this.chapAuth = chapAuth; }

    public String userIpString() {
        return (userIp[0] & 0xff) + "." + (userIp[1] & 0xff) + "." + (userIp[2] & 0xff) + "." + (userIp[3] & 0xff);
    }

    public String getAttrString(byte type) {
        byte[] v = attrs.get(type);
        return v == null ? null : new String(v, StandardCharsets.UTF_8);
    }

    public void putAttr(byte type, String value) {
        attrs.put(type, value.getBytes(StandardCharsets.UTF_8));
    }

    public void putAttr(byte type, byte[] value) {
        attrs.put(type, value);
    }

    /** 解析 UDP 数据报为 Portal 报文，非法返回 null。 */
    public static PortalPacket parse(byte[] data) {
        if (data == null || data.length < 16) return null;
        PortalPacket p = new PortalPacket();
        int o = 0;
        p.version = data[o++];
        if (p.version != VERSION_1 && p.version != VERSION_2) return null;
        p.type = data[o++];
        p.authType = data[o++];
        o += 2; // reserved
        p.serialNo = ((data[o] & 0xff) << 8) | (data[o + 1] & 0xff);
        o += 2;
        p.reqId = data[o++];
        System.arraycopy(data, o, p.userIp, 0, 4);
        o += 4;
        p.userPort = ((data[o] & 0xff) << 8) | (data[o + 1] & 0xff);
        o += 2;
        p.errCode = data[o++];
        int attrNum = data[o++] & 0xff;
        for (int i = 0; i < attrNum; i++) {
            if (o + 2 > data.length) return null;
            byte at = data[o++];
            int len = data[o++] & 0xff;
            if (len < 2 || o + (len - 2) > data.length) return null;
            byte[] v = new byte[len - 2];
            System.arraycopy(data, o, v, 0, len - 2);
            o += len - 2;
            p.attrs.put(at, v);
        }
        // CHAP：尾部 16 字节 ChapAuth
        if (p.authType == AUTH_CHAP && o + 16 <= data.length) {
            byte[] ca = new byte[16];
            System.arraycopy(data, o, ca, 0, 16);
            p.chapAuth = ca;
        }
        return p;
    }

    /** 编码为字节数组（用于回包）。 */
    public byte[] encode() {
        int body = 16;
        for (byte[] v : attrs.values()) body += 2 + v.length;
        boolean chap = authType == AUTH_CHAP && chapAuth != null;
        if (chap) body += 16;
        byte[] out = new byte[body];
        int o = 0;
        out[o++] = version == 0 ? VERSION : version;
        out[o++] = type;
        out[o++] = authType;
        out[o++] = 0; out[o++] = 0; // reserved
        out[o++] = (byte) (serialNo >> 8);
        out[o++] = (byte) serialNo;
        out[o++] = reqId;
        System.arraycopy(userIp, 0, out, o, 4);
        o += 4;
        out[o++] = (byte) (userPort >> 8);
        out[o++] = (byte) userPort;
        out[o++] = errCode;
        out[o++] = (byte) attrs.size();
        for (Map.Entry<Byte, byte[]> e : attrs.entrySet()) {
            out[o++] = e.getKey();
            out[o++] = (byte) (e.getValue().length + 2);
            System.arraycopy(e.getValue(), 0, out, o, e.getValue().length);
            o += e.getValue().length;
        }
        if (chap) {
            System.arraycopy(chapAuth, 0, out, o, 16);
        }
        return out;
    }
}
