import com.nac.auth.radius.protocol.MD4;
import com.nac.auth.radius.protocol.MsChapV2;
import com.nac.auth.radius.protocol.RadiusConstants;
import com.nac.auth.radius.protocol.RadiusPacket;
import com.nac.auth.radius.protocol.RadiusSignatures;

import javax.xml.bind.DatatypeConverter;

/**
 * RFC 2759 §9.3 / 已知向量离线自检（不依赖 Spring/MySQL/Redis）。
 * 运行：java -cp .;<nac-auth target/classes> SelfTest
 */
public class SelfTest {
    static int fail = 0;

    static void check(String name, String actualHex, String expectHex) {
        boolean ok = actualHex.equalsIgnoreCase(expectHex);
        System.out.println((ok ? "PASS " : "FAIL ") + name
                + (ok ? "" : "\n   expect=" + expectHex + "\n   actual=" + actualHex));
        if (!ok) fail++;
    }

    static String hex(byte[] b) { return DatatypeConverter.printHexBinary(b); }

    public static void main(String[] args) throws Exception {
        check("MD4(\"\")", hex(MD4.hash(new byte[0])), "31D6CFE0D16AE931B73C59D7E0C089C0");
        check("MD4(abc)", hex(MD4.hash("abc".getBytes("UTF-8"))), "A448017AAF21D8525FC10AE87AA6729D");

        // RFC 2759 §9.3 向量：用户名 User，密码 clientPass
        byte[] authCh = DatatypeConverter.parseHexBinary("5B5D7C7D7B3F2F3E3C2C602132262628");
        byte[] peerCh = DatatypeConverter.parseHexBinary("21402324255E262A28295F2B3A337C7E");

        check("NtPasswordHash(clientPass)",
                hex(MsChapV2.ntPasswordHash("clientPass")),
                "44EBBA8D5312B8D611474411F56989AE");

        // RFC2759 §9.2 权威向量
        byte[] nt = MsChapV2.generateNtResponse(authCh, peerCh, "User", "clientPass");
        check("GenerateNTResponse", hex(nt),
                "82309ECD8D708B5EA08FAA3981CD83544233114A3D85D6DF");
        check("PasswordHashHash", hex(MD4.hash(MsChapV2.ntPasswordHash("clientPass"))),
                "41C00C584BD2D91C4017A2A12FA59F3F");

        byte[] ar = MsChapV2.generateAuthenticatorResponse(peerCh, authCh, "User", "clientPass", nt);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 20; i++) s.append(String.format("%02X", ar[i] & 0xFF));
        check("AuthenticatorResponse(S=)", s.toString(),
                "407A5589115FD0D6209F510FE9C04566932CDA56");

        // MPPE 密钥派生（RFC3079 §3.5.3 向量）：send=405CB224...，send != recv
        byte[][] k = MsChapV2.deriveMppeSendRecvKeys("clientPass", nt);
        System.out.println("MPPE-Send=" + hex(k[0]) + " (" + k[0].length + "B)");
        System.out.println("MPPE-Recv=" + hex(k[1]) + " (" + k[1].length + "B)");
        check("MPPE-SendKey(RFC3079)", hex(k[0]), "405CB2247A7956E6E211007AE27B22D4");
        if (k[0].length != 16 || k[1].length != 16 || java.util.Arrays.equals(k[0], k[1])) {
            System.out.println("FAIL MPPE key derivation"); fail++;
        } else System.out.println("PASS MPPE key derivation");

        // MPPE 密钥加密往返结构：salt + 32B 密文（16B key -> 2 个 16B 块... 16/14=2 块 => 2+32=34）
        byte[] salt = {(byte) 0x85, 0x12};
        byte[] enc = MsChapV2.encryptMppeKey(k[0], "testing123", authCh, salt);
        System.out.println("Enc-MPPE len=" + enc.length + ", saltHighBit=" + ((enc[0] & 0x80) != 0));
        if (enc.length != 34 || (enc[0] & 0x80) == 0 || enc[1] != 0x12) {
            System.out.println("FAIL MPPE encryption framing"); fail++;
        } else System.out.println("PASS MPPE encryption framing");

        // ===== RFC 3579 §3.2：回复包 Message-Authenticator 必须先于 Response Authenticator =====
        // 完整复刻 RadiusServerHandler.sendResponse 的签名流程，再按 NAS 视角独立验签
        String secret = "testing123";
        byte[] requestAuth = DatatypeConverter.parseHexBinary("0102030405060708090A0B0C0D0E0F10");
        RadiusPacket ch = new RadiusPacket(RadiusConstants.CODE_ACCESS_CHALLENGE, 7);
        ch.addAttribute(RadiusConstants.ATTR_EAP_MESSAGE, new byte[]{1, 7, 0, 6});
        ch.addAttribute(RadiusConstants.ATTR_MESSAGE_AUTHENTICATOR, new byte[16]);

        byte[] pkt = ch.encodeWithAuthenticator(requestAuth);
        int[] ma = RadiusSignatures.locateAttribute(pkt, RadiusConstants.ATTR_MESSAGE_AUTHENTICATOR);
        if (ma == null) { System.out.println("FAIL RFC3579: attr80 missing"); fail++; }
        else {
            // 1) HMAC 覆盖 Authenticator=RequestAuth、attr80 全 0 的报文
            byte[] hmac = RadiusSignatures.hmacMd5(pkt, secret);
            System.arraycopy(hmac, 0, pkt, ma[0] + 2, 16);
            // 2) 最后才计算 Response Authenticator
            byte[] ra = RadiusPacket.computeResponseAuthenticator(pkt, requestAuth, secret);
            System.arraycopy(ra, 0, pkt, 4, 16);

            // NAS 侧验 Message-Authenticator：恢复 RequestAuth 到头部、attr80 清零再算 HMAC
            byte[] forHmac = pkt.clone();
            System.arraycopy(requestAuth, 0, forHmac, 4, 16);
            for (int i = 0; i < 16; i++) forHmac[ma[0] + 2 + i] = 0;
            byte[] calcHmac = RadiusSignatures.hmacMd5(forHmac, secret);
            boolean hmacOk = true;
            for (int i = 0; i < 16; i++) hmacOk &= calcHmac[i] == pkt[ma[0] + 2 + i];
            System.out.println((hmacOk ? "PASS " : "FAIL ") + "RFC3579 Message-Authenticator(HMAC 顺序)");
            if (!hmacOk) fail++;

            // NAS 侧验 Response Authenticator：MD5(code+id+len+RequestAuth+最终属性+secret)
            byte[] forAuth = pkt.clone();
            byte[] calcRa = RadiusPacket.computeResponseAuthenticator(forAuth, requestAuth, secret);
            boolean raOk = true;
            for (int i = 0; i < 16; i++) raOk &= calcRa[i] == pkt[4 + i];
            System.out.println((raOk ? "PASS " : "FAIL ") + "RFC2865 Response Authenticator(含最终 attr80)");
            if (!raOk) fail++;
        }

        System.out.println(fail == 0 ? "ALL PASS" : (fail + " CHECK(S) FAILED"));
        System.exit(fail == 0 ? 0 : 2);
    }
}
