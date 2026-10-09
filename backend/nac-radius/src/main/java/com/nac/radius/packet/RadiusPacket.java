package com.nac.radius.packet;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RADIUS 报文内存模型。属性按 Type 存放，同 Type 多值时保留列表顺序。
 * 不可变语义由使用方保证（解析后只读，构造响应时新建）。
 */
public class RadiusPacket {

    private final byte code;
    private final byte identifier;
    private final byte[] requestAuthenticator; // 请求认证符（16B），响应时用于计算响应认证符
    private final Map<Integer, List<byte[]>> attributes = new LinkedHashMap<>();
    private InetSocketAddress sender; // 对端 NAS 地址（响应回包用）

    public RadiusPacket(byte code, byte identifier, byte[] requestAuthenticator) {
        this.code = code;
        this.identifier = identifier;
        this.requestAuthenticator = requestAuthenticator;
    }

    public byte getCode() { return code; }
    public byte getIdentifier() { return identifier; }
    public byte[] getRequestAuthenticator() { return requestAuthenticator; }
    public InetSocketAddress getSender() { return sender; }
    public void setSender(InetSocketAddress sender) { this.sender = sender; }

    public void addAttribute(int type, byte[] value) {
        attributes.computeIfAbsent(type, k -> new ArrayList<>()).add(value);
    }

    public void addString(int type, String value) {
        addAttribute(type, value.getBytes(StandardCharsets.UTF_8));
    }

    public void addInt(int type, int value) {
        byte[] v = new byte[4];
        v[0] = (byte) (value >>> 24);
        v[1] = (byte) (value >>> 16);
        v[2] = (byte) (value >>> 8);
        v[3] = (byte) value;
        addAttribute(type, v);
    }

    /** 取某 Type 的第一个值，无则 null */
    public byte[] getAttr(int type) {
        List<byte[]> list = attributes.get(type);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    public String getString(int type) {
        byte[] v = getAttr(type);
        return v == null ? null : new String(v, StandardCharsets.UTF_8);
    }

    public Integer getInt(int type) {
        byte[] v = getAttr(type);
        if (v == null || v.length < 4) return null;
        return ((v[0] & 0xFF) << 24) | ((v[1] & 0xFF) << 16) | ((v[2] & 0xFF) << 8) | (v[3] & 0xFF);
    }

    public Map<Integer, List<byte[]>> getAttributes() {
        return attributes;
    }

    /**
     * 判定本报文 EAP-Message 实际所在的属性类型。
     * 标准为 87，但部分厂商（如华为交换机）会把 EAP 放在 79，并用 87 承载私有端口信息。
     * 判定依据：属性首个值的 EAP code 字节 ∈ {1..4} 且长度字段 ≥4，以此稳健区分真正的 EAP。
     * 优先返回标准 87，其次 79，均无有效 EAP 则返回 -1。
     */
    public int eapAttributeType() {
        for (int t : new int[]{RadiusCodes.EAP_MESSAGE, RadiusCodes.EAP_MESSAGE_ALT}) {
            byte[] v = getAttr(t);
            if (v == null || v.length < 4) continue;
            int code = v[0] & 0xFF;
            int len = ((v[2] & 0xFF) << 8) | (v[3] & 0xFF);
            if (code >= 1 && code <= 4 && len >= 4) return t;
        }
        return -1;
    }

    public boolean hasEap() {
        return eapAttributeType() > 0;
    }

    /** 取 EAP-Message 的全部值（用于分片重组），按请求实际使用的类型取。 */
    public List<byte[]> eapValues() {
        int t = eapAttributeType();
        if (t < 0) return java.util.Collections.emptyList();
        List<byte[]> list = attributes.get(t);
        return list == null ? java.util.Collections.emptyList() : list;
    }
}
