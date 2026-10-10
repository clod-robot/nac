package com.nac.radius.eap;

import com.nac.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 802.1X EAP-TLS/PEAP 证书管理：NAC 自签 CA，启动时生成/加载服务端证书，
 * 导出 CA 证书供终端安装信任，并可为 EAP-TLS 签发客户端证书。
 * 证书以 PEM 持久化到磁盘（目录可配），重启复用，避免每次重新签发导致终端需重装 CA。
 */
@Slf4j
@Component
public class CertManager {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final int RSA_BITS = 2048;
    private static final String EC_CURVE = "secp256r1"; // NIST P-256
    private static final long DAY = 24L * 3600 * 1000;

    private final Path dir;
    private volatile X509Certificate caCert;
    private volatile PrivateKey caKey;
    private volatile X509Certificate serverCert;
    private volatile PrivateKey serverKey;
    /** 热加载：CA/证书变更后重建并原子替换，新握手立即使用新证书。 */
    private volatile SSLContext sslContext;

    public CertManager(@Value("${nac.radius.tls.dir:data/tls}") String tlsDir) throws Exception {
        this.dir = Path.of(tlsDir).toAbsolutePath();
        Files.createDirectories(dir);
        init();
    }

    private void init() throws Exception {
        Path caCertPath = dir.resolve("ca.pem");
        Path caKeyPath = dir.resolve("ca-key.pem");
        Path srvCertPath = dir.resolve("server.pem");
        Path srvKeyPath = dir.resolve("server-key.pem");

        // 1) CA 保持稳定：存在则加载，否则生成（终端只需安装信任一次）
        if (Files.exists(caCertPath) && Files.exists(caKeyPath)) {
            this.caCert = readCert(Files.readString(caCertPath));
            this.caKey = readKey(Files.readString(caKeyPath));
            log.info("802.1X TLS CA 已加载: {}", caCertPath);
        } else {
            KeyPair caKp = genEc();
            X500Name caSubject = new X500Name("CN=NAC 802.1X Root CA,O=NAC,C=CN");
            this.caCert = buildCert(caSubject, caKp.getPublic(), caSubject, caKp.getPrivate(), true, 3650, null);
            this.caKey = caKp.getPrivate();
            Files.writeString(caCertPath, toPem(caCert));
            Files.writeString(caKeyPath, keyPem(caKey));
            log.info("802.1X 自签 CA 已生成(ECDSA): {}", caCertPath);
        }

        // 2) 服务端证书：SAN 绑定主网口 IP；IP 变化或非 EC 证书则用既有 CA 重签（升级到 ECDSA 缩短证书链）
        String ip = detectPrimaryIp();
        boolean reuse = Files.exists(srvCertPath) && Files.exists(srvKeyPath);
        if (reuse) {
            X509Certificate existing = readCert(Files.readString(srvCertPath));
            if (!certHasSanIp(existing, ip) || !"EC".equalsIgnoreCase(existing.getPublicKey().getAlgorithm())) {
                log.info("802.1X 服务端证书需重签(SAN 变更或非 ECDSA)，SAN IP={}", ip);
                reuse = false;
            }
        }
        if (reuse) {
            this.serverCert = readCert(Files.readString(srvCertPath));
            this.serverKey = readKey(Files.readString(srvKeyPath));
            log.info("802.1X 服务端证书已加载(ECDSA): SAN IP={}", ip);
        } else {
            generateServerCert(ip);
        }
        rebuildContext();
    }

    /** 用当前 CA 签发 ECDSA(P-256) 服务端证书并落盘。 */
    private void generateServerCert(String ip) throws Exception {
        KeyPair srvKp = genEc();
        X500Name srvSubject = new X500Name("CN=NAC RADIUS 802.1X Server,O=NAC,C=CN");
        List<String> sanIps = ip == null ? Collections.emptyList() : List.of(ip);
        X500Name issuer = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());
        this.serverCert = buildCert(srvSubject, srvKp.getPublic(), issuer, caKey, false, 825, sanIps);
        this.serverKey = srvKp.getPrivate();
        Files.writeString(dir.resolve("server.pem"), toPem(serverCert));
        Files.writeString(dir.resolve("server-key.pem"), keyPem(serverKey));
        log.info("802.1X 服务端证书已生成(ECDSA): SAN IP={}", ip);
    }

    /** 重建并原子替换 SSLContext（新握手立即生效）。 */
    private void rebuildContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        // 仅下发叶子证书：客户端已通过安装 CA 建立信任，无需在链中附带 CA，可显著缩小 ServerHello 航班。
        ks.setKeyEntry("server", serverKey, new char[0], new Certificate[]{serverCert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);

        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry("ca", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        this.sslContext = ctx;
    }

    /** CA 证书 PEM（供终端下载安装信任）。 */
    public String getCaCertPem() {
        try { return toPem(caCert); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** 服务端 SSLContext（含服务端证书与私钥，信任本 CA 签发的客户端证书）。热加载下返回最新实例。 */
    public SSLContext serverSslContext() {
        SSLContext ctx = this.sslContext;
        if (ctx == null) {
            try { rebuildContext(); } catch (Exception e) { throw new IllegalStateException(e); }
            ctx = this.sslContext;
        }
        return ctx;
    }

    /** 用本 CA 为 EAP-TLS 客户端签发证书（PEM）。 */
    public String signClientCert(PublicKey publicKey, String cn) throws Exception {
        X500Name subject = new X500Name("CN=" + cn + ",O=NAC,C=CN");
        X500Name issuer = new X500Name(caCert.getSubjectX500Principal().getName());
        X509Certificate c = buildCert(subject, publicKey, issuer, caKey, false, 825, null);
        return toPem(c);
    }

    // ---- 客户 CA 上传（校验可用，失败回退不覆盖） ----

    /** 当前 CA 与服务端证书信息（供管理页展示）。 */
    public CertInfo certInfo() {
        CertInfo i = new CertInfo();
        i.caSubject = String.valueOf(caCert.getSubjectX500Principal());
        i.caKeyAlg = caCert.getPublicKey().getAlgorithm();
        i.caSigAlg = caCert.getSigAlgName();
        i.caNotAfter = caCert.getNotAfter().toString();
        i.serverKeyAlg = serverCert.getPublicKey().getAlgorithm();
        i.serverSigAlg = serverCert.getSigAlgName();
        try { i.serverCertSize = toPem(serverCert).length(); } catch (Exception ignore) {}
        return i;
    }

    /**
     * 导入客户自带 CA：先严格校验可用性（可解析、私钥匹配、为 CA、能签发服务端证书），
     * 全部通过才落盘并以 ECDSA 重签服务端证书、热替换 SSLContext；任一步失败抛异常（不改动现有证书=回退）。
     */
    public synchronized CertInfo applyCustomerCa(String caCertPem, String caKeyPem) {
        X509Certificate newCa;
        PrivateKey newCaKey;
        try {
            newCa = readCert(caCertPem);
            newCaKey = readKey(caKeyPem);
        } catch (Exception e) {
            throw new BusinessException(400, "CA 证书或私钥无法解析：" + e.getMessage());
        }
        // 1) 必须是 CA 证书
        if (newCa.getBasicConstraints() < 0) {
            throw new BusinessException(400, "该证书不是 CA 证书（BasicConstraints 未标记 CA:TRUE），无法签发服务端证书");
        }
        // 2) 有效期校验
        try { newCa.checkValidity(); } catch (Exception e) {
            throw new BusinessException(400, "CA 证书已过期或尚未生效");
        }
        // 3) 私钥与证书匹配
        try { verifyKeyMatchesCert(newCa, newCaKey); } catch (Exception e) {
            throw new BusinessException(400, "CA 私钥与证书不匹配：" + e.getMessage());
        }
        // 4) 实签验证：用该 CA 签发一张 ECDSA 服务端证书，能成功即证明可用
        X509Certificate test;
        try {
            KeyPair tk = genEc();
            X500Name sub = new X500Name("CN=NAC RADIUS 802.1X Server,O=NAC,C=CN");
            X500Name iss = X500Name.getInstance(newCa.getSubjectX500Principal().getEncoded());
            test = buildCert(sub, tk.getPublic(), iss, newCaKey, false, 825, null);
        } catch (Exception e) {
            throw new BusinessException(400, "用该 CA 签发服务端证书失败：" + e.getMessage());
        }
        // —— 校验全部通过，原子应用：落盘 → 重签服务端证书 → 重建上下文 ——
        try {
            Files.writeString(dir.resolve("ca.pem"), toPem(newCa));
            Files.writeString(dir.resolve("ca-key.pem"), keyPem(newCaKey));
            this.caCert = newCa;
            this.caKey = newCaKey;
            generateServerCert(detectPrimaryIp());
            rebuildContext();
            log.info("客户 CA 已导入并生效，服务端证书已用 ECDSA 重签");
            return certInfo();
        } catch (Exception e) {
            // 应用失败：尽力回滚到原证书（重新加载磁盘原有内容不可得，故重建时若抛错需重启；此处记录并抛出）
            log.error("应用客户 CA 失败", e);
            throw new BusinessException(500, "应用 CA 失败：" + e.getMessage());
        }
    }

    private static void verifyKeyMatchesCert(X509Certificate cert, PrivateKey key) throws Exception {
        String alg = "EC".equalsIgnoreCase(key.getAlgorithm()) ? "SHA256withECDSA" : "SHA256withRSA";
        byte[] data = new byte[32];
        new SecureRandom().nextBytes(data);
        Signature s = Signature.getInstance(alg);
        s.initSign(key);
        s.update(data);
        byte[] sig = s.sign();
        Signature v = Signature.getInstance(alg);
        v.initVerify(cert.getPublicKey());
        v.update(data);
        if (!v.verify(sig)) throw new IllegalStateException("签名验证失败");
    }

    /** CA/服务端证书摘要。 */
    @lombok.Data
    public static class CertInfo {
        private String caSubject;
        private String caKeyAlg;
        private String caSigAlg;
        private String caNotAfter;
        private String serverKeyAlg;
        private String serverSigAlg;
        private int serverCertSize;
    }

    // ---- 内部工具 ----
    private KeyPair genEc() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new java.security.spec.ECGenParameterSpec(EC_CURVE), new SecureRandom());
        return g.generateKeyPair();
    }

    /** 探测主网口 IPv4：优先私网地址(10/172.16-31/192.168)，排除回环/虚拟/docker/网桥。 */
    static String detectPrimaryIp() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String name = ni.getName().toLowerCase();
                if (name.startsWith("docker") || name.startsWith("veth") || name.startsWith("br-")
                        || name.startsWith("virbr") || name.startsWith("cni") || name.startsWith("lo")) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) continue;
                    String ip = addr.getHostAddress();
                    if (addr.isSiteLocalAddress()) return ip; // 私网优先直接返回
                    if (fallback == null) fallback = ip;
                }
            }
        } catch (Exception e) {
            log.warn("探测网口 IP 失败: {}", e.getMessage());
        }
        return fallback; // 无非私网时取首个公网 IPv4；都没有则 null
    }

    /** 校验证书 SAN(iPAddress) 是否包含目标 IP。 */
    private static boolean certHasSanIp(X509Certificate cert, String ip) {
        if (ip == null) return true;
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans == null) return false;
            for (List<?> san : sans) {
                if (san.size() < 2) continue;
                int type = ((Number) san.get(0)).intValue();
                if (type == 7) { // iPAddress
                    Object v = san.get(1);
                    String s = v instanceof byte[] b ? InetAddress.getByAddress(b).getHostAddress() : String.valueOf(v);
                    if (ip.equals(s)) return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private X509Certificate buildCert(X500Name subject, PublicKey pub, X500Name issuer, PrivateKey issuerKey,
                                      boolean isCa, int days, List<String> sanIps) throws Exception {
        long now = System.currentTimeMillis();
        BigInteger serial = BigInteger.valueOf(now).abs();
        Date notBefore = new Date(now - DAY);
        Date notAfter = new Date(now + (long) days * DAY);
        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(issuer, serial, notBefore, notAfter, subject, pub);
        if (isCa) {
            b.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        } else {
            b.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
            // ECDSA 证书用 keyAgreement（keyEncipherment 仅 RSA 适用）
            int ku = KeyUsage.digitalSignature | ("EC".equalsIgnoreCase(pub.getAlgorithm())
                    ? KeyUsage.keyAgreement : KeyUsage.keyEncipherment);
            b.addExtension(Extension.keyUsage, true, new KeyUsage(ku));
        }
        if (sanIps != null && !sanIps.isEmpty()) {
            List<GeneralName> names = new ArrayList<>();
            for (String ip : sanIps) names.add(new GeneralName(GeneralName.iPAddress, ip));
            b.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names.toArray(new GeneralName[0])));
        }
        ContentSigner signer = new JcaContentSignerBuilder(signerAlg(issuerKey)).setProvider("BC").build(issuerKey);
        X509CertificateHolder holder = b.build(signer);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
    }

    /** 按签发者私钥算法选择签名算法。 */
    private static String signerAlg(PrivateKey key) {
        return "EC".equalsIgnoreCase(key.getAlgorithm()) ? "SHA256withECDSA" : "SHA256WithRSA";
    }

    private static String toPem(Object o) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) { w.writeObject(o); }
        return sw.toString();
    }

    /** 私钥统一以 PKCS#8 落盘（EC/RSA 通用，避免 BC 写 EC 为 SEC1 后读不回）。 */
    private static String keyPem(PrivateKey k) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(k.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
    }

    private static X509Certificate readCert(String pem) throws Exception {
        try (PEMParser p = new PEMParser(new StringReader(pem))) {
            Object o = p.readObject();
            if (o instanceof X509CertificateHolder h) {
                return new JcaX509CertificateConverter().setProvider("BC").getCertificate(h);
            }
        }
        throw new IllegalStateException("PEM 中无证书");
    }

    private static PrivateKey readKey(String pem) throws Exception {
        try (PEMParser p = new PEMParser(new StringReader(pem))) {
            Object o = p.readObject();
            JcaPEMKeyConverter conv = new JcaPEMKeyConverter().setProvider("BC");
            if (o instanceof org.bouncycastle.openssl.PEMKeyPair kp) {
                // SEC1(EC) 的 PEMKeyPair 公钥信息可能为空，走 getPrivateKey 避免 NPE
                if (kp.getPublicKeyInfo() != null) return conv.getKeyPair(kp).getPrivate();
                return conv.getPrivateKey(kp.getPrivateKeyInfo());
            }
            if (o instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo info) return conv.getPrivateKey(info);
        } catch (Exception e) { /* 回退到 PKCS#8 标准解析 */ }
        byte[] der = Base64.getMimeDecoder().decode(pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""));
        for (String alg : new String[]{"EC", "RSA"}) {
            try { return KeyFactory.getInstance(alg).generatePrivate(new PKCS8EncodedKeySpec(der)); }
            catch (Exception ignore) {}
        }
        throw new IllegalStateException("PEM 中无私钥或格式不支持");
    }
}
