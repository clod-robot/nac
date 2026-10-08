package com.nac.radius.eap;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
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
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

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

    private static final String SIG_ALG = "SHA256WithRSA";
    private static final int RSA_BITS = 2048;
    private static final long DAY = 24L * 3600 * 1000;

    private final Path dir;
    private X509Certificate caCert;
    private PrivateKey caKey;
    private X509Certificate serverCert;
    private PrivateKey serverKey;

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

        if (Files.exists(caCertPath) && Files.exists(caKeyPath) && Files.exists(srvCertPath) && Files.exists(srvKeyPath)) {
            this.caCert = readCert(Files.readString(caCertPath));
            this.caKey = readKey(Files.readString(caKeyPath));
            this.serverCert = readCert(Files.readString(srvCertPath));
            this.serverKey = readKey(Files.readString(srvKeyPath));
            log.info("802.1X TLS 证书已加载: {}", dir);
            return;
        }
        // 生成自签 CA
        KeyPair caKp = genRsa();
        X500Name caSubject = new X500Name("CN=NAC 802.1X Root CA,O=NAC,C=CN");
        this.caCert = buildCert(caSubject, caKp.getPublic(), caSubject, caKp.getPrivate(), true, 3650);
        this.caKey = caKp.getPrivate();
        // 生成服务端证书（由 CA 签发）
        KeyPair srvKp = genRsa();
        X500Name srvSubject = new X500Name("CN=NAC RADIUS 802.1X Server,O=NAC,C=CN");
        this.serverCert = buildCert(srvSubject, srvKp.getPublic(), caSubject, caKp.getPrivate(), false, 825);
        this.serverKey = srvKp.getPrivate();

        Files.writeString(caCertPath, toPem(caCert));
        Files.writeString(caKeyPath, toPem(caKey));
        Files.writeString(srvCertPath, toPem(serverCert));
        Files.writeString(srvKeyPath, toPem(serverKey));
        log.info("802.1X 自签 CA 与服务端证书已生成: {}", dir);
    }

    /** CA 证书 PEM（供终端下载安装信任）。 */
    public String getCaCertPem() {
        try { return toPem(caCert); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** 服务端 SSLContext（含服务端证书与私钥，信任本 CA 签发的客户端证书）。 */
    public SSLContext serverSslContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("server", serverKey, new char[0], new Certificate[]{serverCert, caCert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);

        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry("ca", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return ctx;
    }

    /** 用本 CA 为 EAP-TLS 客户端签发证书（PEM）。 */
    public String signClientCert(PublicKey publicKey, String cn) throws Exception {
        X500Name subject = new X500Name("CN=" + cn + ",O=NAC,C=CN");
        X500Name issuer = new X500Name(caCert.getSubjectX500Principal().getName());
        X509Certificate c = buildCert(subject, publicKey, issuer, caKey, false, 825);
        return toPem(c);
    }

    // ---- 内部工具 ----
    private KeyPair genRsa() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(RSA_BITS, new SecureRandom());
        return g.generateKeyPair();
    }

    private X509Certificate buildCert(X500Name subject, PublicKey pub, X500Name issuer, PrivateKey issuerKey,
                                      boolean isCa, int days) throws Exception {
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
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        }
        ContentSigner signer = new JcaContentSignerBuilder(SIG_ALG).setProvider("BC").build(issuerKey);
        X509CertificateHolder holder = b.build(signer);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
    }

    private static String toPem(Object o) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) { w.writeObject(o); }
        return sw.toString();
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
            if (o instanceof org.bouncycastle.openssl.PEMKeyPair kp) return conv.getKeyPair(kp).getPrivate();
            if (o instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo info) return conv.getPrivateKey(info);
            throw new IllegalStateException("PEM 中无私钥: " + (o == null ? "null" : o.getClass()));
        }
    }

    /** 生成 PKCS#10 CSR（占位保留，供后续接口化签发使用）。 */
    @SuppressWarnings("unused")
    private PKCS10CertificationRequest csr(KeyPair kp, String cn) throws Exception {
        PKCS10CertificationRequestBuilder b = new JcaPKCS10CertificationRequestBuilder(
                new X500Name("CN=" + cn), kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder(SIG_ALG).setProvider("BC").build(kp.getPrivate());
        return b.build(signer);
    }
}
