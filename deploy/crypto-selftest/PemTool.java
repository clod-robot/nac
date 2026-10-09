import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * 临时工具：把 openssl 产物（PKCS1 私钥 + PEM 证书）转换为 JKS。
 * 用法：
 *   java PemTool make-ca <ca.key> <ca.crt> <out.jks> <password>          # 构建可签发 CA keystore
 *   java PemTool print <ca.crt>                                          # 打印证书信息
 */
public class PemTool {

    static byte[] pemDer(String path) throws IOException {
        String s = new String(Files.readAllBytes(Paths.get(path)), "UTF-8");
        int h = s.indexOf("-----");
        if (h < 0) throw new IOException("不是 PEM 文件: " + path);
        h = s.indexOf("-----", h + 5) + 5;          // BEGIN 行的结束位置
        int t = s.indexOf("-----END", h);            // END 标记开始
        if (t < 0) throw new IOException("PEM 缺少 END 标记: " + path);
        String b64 = s.substring(h, t).replaceAll("\\s", "");
        return Base64.getMimeDecoder().decode(b64);
    }

    /** PKCS1 RSAPrivateKey 包一层 PKCS8 PrivateKeyInfo 头（OID 1.2.840.113549.1.1.1） */
    static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] algIdContent = {
                0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01,
                0x05, 0x00};
        byte[] algId = der(0x30, algIdContent);
        byte[] octet = der(0x04, pkcs1);
        ByteArrayOutputStream c = new ByteArrayOutputStream();
        try {
            c.write(version); c.write(algId); c.write(octet);
        } catch (IOException ignored) {}
        return der(0x30, c.toByteArray());
    }

    static byte[] der(int tag, byte[] content) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        int len = content.length;
        if (len < 0x80) {
            o.write(len);
        } else {
            int n = (Integer.SIZE - Integer.numberOfLeadingZeros(len) + 7) / 8;
            o.write(0x80 | n);
            for (int i = n - 1; i >= 0; i--) o.write((len >> (8 * i)) & 0xFF);
        }
        try { o.write(content); } catch (IOException ignored) {}
        return o.toByteArray();
    }

    static Certificate[] certs(String path) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream in = new FileInputStream(path)) {
            return cf.generateCertificates(in).toArray(new Certificate[0]);
        }
    }

    public static void main(String[] args) throws Exception {
        String cmd = args[0];
        if (cmd.equals("print")) {
            for (Certificate c : certs(args[1])) System.out.println(c);
            return;
        }
        // make-ca <ca.key> <ca.crt> <out.jks> <password>
        // make-server <server.key> <server.crt> <ca.crt> <out.jks> <password>  alias=peap
        String keyPath = args[1], leafPath = args[2];
        String caPath = args[0].equals("make-server") ? args[3] : args[2];
        String outJks  = args[0].equals("make-server") ? args[4] : args[3];
        String pass    = args[args.length - 1];
        String alias   = args[0].equals("make-server") ? "peap" : "ca";

        byte[] pkcs1 = pemDer(keyPath);
        byte[] pkcs8 = pkcs1ToPkcs8(pkcs1);
        PrivateKey key = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        Certificate[] caChain = certs(caPath);
        Certificate[] chain = args[0].equals("make-server")
                ? new Certificate[]{certs(leafPath)[0], caChain[0]} : caChain;
        char[] p = pass.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setKeyEntry(alias, key, p, chain);
        try (OutputStream out = Files.newOutputStream(Paths.get(outJks))) {
            ks.store(out, p);
        }
        System.out.println("JKS OK: " + outJks + " (alias=" + alias + ", chain=" + chain.length
                + ", key=RSA-" + ((java.security.interfaces.RSAPrivateKey) key)
                .getModulus().bitLength() + ")");
    }
}
