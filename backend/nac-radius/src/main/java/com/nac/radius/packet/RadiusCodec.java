package com.nac.radius.packet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RADIUS 编解码与密码学工具（RFC 2865 5.2 / RFC 2865 3）。
 * 全部使用 MD5（协议强制），仅用于协议字段，不用于口令存储。
 */
public final class RadiusCodec {

    private RadiusCodec() {}

    private static final int AUTH_LEN = 16;

    /** 解析 UDP 报文为 RadiusPacket；格式非法返回 null（调用方丢弃）。 */
    public static RadiusPacket parse(byte[] data, int length) {
        if (data == null || length < RadiusCodes.HEADER_LEN) return null;
        byte code = data[0];
        byte identifier = data[1];
        int len = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        if (len < RadiusCodes.HEADER_LEN || len > length) return null;
        byte[] auth = Arrays.copyOfRange(data, 4, 20);
        RadiusPacket pkt = new RadiusPacket(code, identifier, auth);
        int off = 20;
        while (off + 2 <= len) {
            int type = data[off] & 0xFF;
            int alen = data[off + 1] & 0xFF;
            if (alen < 2 || off + alen > len) return null; // 非法 TLV，整包丢弃
            byte[] val = Arrays.copyOfRange(data, off + 2, off + alen);
            pkt.addAttribute(type, val);
            off += alen;
        }
        return pkt;
    }

    /**
     * 序列化响应报文：Code + Identifier + Length + Response-Authenticator + Attributes。
     * Response-Authenticator = MD5(Code + ID + Length + RequestAuth + Attrs + Secret)。
     */
    public static byte[] encodeResponse(RadiusPacket response, byte[] requestAuthenticator, String sharedSecret) {
        byte[] attrs = encodeAttributes(response);
        int total = RadiusCodes.HEADER_LEN + attrs.length;
        byte[] out = new byte[total];
        out[0] = response.getCode();
        out[1] = response.getIdentifier();
        out[2] = (byte) (total >>> 8);
        out[3] = (byte) total;
        // 先填空认证符参与计算
        System.arraycopy(requestAuthenticator, 0, out, 4, AUTH_LEN);
        System.arraycopy(attrs, 0, out, 20, attrs.length);
        byte[] ra = md5(concat(out, sharedSecret.getBytes(StandardCharsets.UTF_8)));
        System.arraycopy(ra, 0, out, 4, AUTH_LEN);
        // 若包含 Message-Authenticator(80) 占位（16 字节 0），按 RFC 3579 计算 HMAC-MD5 回填
        fillMessageAuthenticator(out, sharedSecret);
        return out;
    }

    /** 在已填充 Response-Authenticator 的报文上计算并回填 Message-Authenticator（HMAC-MD5）。 */
    private static void fillMessageAuthenticator(byte[] out, String sharedSecret) {
        int off = RadiusCodes.HEADER_LEN;
        while (off + 2 <= out.length) {
            int type = out[off] & 0xFF;
            int alen = out[off + 1] & 0xFF;
            if (alen < 2 || off + alen > out.length) break;
            if (type == RadiusCodes.MESSAGE_AUTHENTICATOR && alen == 18) {
                // 占位需为 16 字节 0；计算时其位置保持 0
                try {
                    Mac mac = Mac.getInstance("HmacMD5");
                    mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacMD5"));
                    byte[] hmac = mac.doFinal(out);
                    System.arraycopy(hmac, 0, out, off + 2, 16);
                } catch (Exception e) {
                    throw new IllegalStateException("HmacMD5 unavailable", e);
                }
                return;
            }
            off += alen;
        }
    }

    private static byte[] encodeAttributes(RadiusPacket pkt) {
        int size = 0;
        for (Map.Entry<Integer, List<byte[]>> e : pkt.getAttributes().entrySet()) {
            for (byte[] v : e.getValue()) size += 2 + v.length;
        }
        byte[] out = new byte[size];
        int off = 0;
        for (Map.Entry<Integer, List<byte[]>> e : pkt.getAttributes().entrySet()) {
            int type = e.getKey();
            for (byte[] v : e.getValue()) {
                out[off++] = (byte) type;
                out[off++] = (byte) (v.length + 2);
                System.arraycopy(v, 0, out, off, v.length);
                off += v.length;
            }
        }
        return out;
    }

    /**
     * PAP 解密 User-Password（RFC 2865 5.2）。
     * p1 = c1 XOR MD5(secret + RA); pi = ci XOR MD5(secret + c(i-1))；末尾补零去除。
     */
    public static String decryptPapPassword(byte[] encrypted, byte[] requestAuthenticator, String sharedSecret) {
        if (encrypted == null || encrypted.length == 0 || encrypted.length % 16 != 0) return null;
        byte[] secret = sharedSecret.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[encrypted.length];
        byte[] prev = requestAuthenticator;
        for (int i = 0; i < encrypted.length; i += 16) {
            byte[] block = Arrays.copyOfRange(encrypted, i, i + 16);
            byte[] hash = md5(concat(secret, prev));
            for (int j = 0; j < 16; j++) result[i + j] = (byte) (block[j] ^ hash[j]);
            prev = block;
        }
        int end = result.length;
        while (end > 0 && result[end - 1] == 0) end--;
        return new String(result, 0, end, StandardCharsets.UTF_8);
    }

    /**
     * CHAP 校验：chapResponse == MD5(chapIdent + plainPassword + chapChallenge)。
     * chapPassword 属性结构：第 1 字节为 CHAP-Ident，其余 16 字节为 CHAP Response。
     * challenge 取自请求认证符（多数 NAS 如此），若属性 60(CHAP-Challenge) 存在则优先。
     */
    public static boolean verifyChap(byte[] chapPassword, String plainPassword, byte[] challenge) {
        if (chapPassword == null || chapPassword.length != 17 || plainPassword == null) return false;
        byte chapIdent = chapPassword[0];
        byte[] resp = Arrays.copyOfRange(chapPassword, 1, 17);
        byte[] pw = plainPassword.getBytes(StandardCharsets.UTF_8);
        byte[] buf = new byte[1 + pw.length + challenge.length];
        buf[0] = chapIdent;
        System.arraycopy(pw, 0, buf, 1, pw.length);
        System.arraycopy(challenge, 0, buf, 1 + pw.length, challenge.length);
        return MessageDigest.isEqual(md5(buf), resp);
    }

    public static byte[] md5(byte[] data) {
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
