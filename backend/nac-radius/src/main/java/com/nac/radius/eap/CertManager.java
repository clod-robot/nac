package com.nac.radius.eap;

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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
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

        // 1) CA 保持稳定：存在则加载，否则生成（终端只需安装信任一次）
        if (Files.exists(caCertPath) && Files.exists(caKeyPath)) {
            this.caCert = readCert(Files.readString(caCertPath));
            this.caKey = readKey(Files.readString(caKeyPath));
            log.info("802.1X TLS CA 已加载: {}", caCertPath);
        } else {
            KeyPair caKp = genRsa();
            X500Name caSubject = new X500Name("CN=NAC 802.1X Root CA,O=NAC,C=CN");
            this.caCert = buildCert(caSubject, caKp.getPublic(), caSubject, caKp.getPrivate(), true, 3650, null);
            this.caKey = caKp.getPrivate();
            Files.writeString(caCertPath, toPem(caCert));
            Files.writeString(caKeyPath, toPem(caKey));
            log.info("802.1X 自签 CA 已生成: {}", caCertPath);
        }

        // 2) 服务端证书：SAN 绑定主网口 IP；IP 变化则用既有 CA 重签
        String ip = detectPrimaryIp();
        boolean reuse = Files.exists(srvCertPath) && Files.exists(srvKeyPath);
        if (reuse) {
            X509Certificate existing = readCert(Files.readString(srvCertPath));
            if (!certHasSanIp(existing, ip)) {
                log.info("802.1X 网口 IP 变更(证书 SAN 不含 {})，重签服务端证书", ip);
                reuse = false;
            }
        }
        if (reuse) {
            this.serverCert = readCert(Files.readString(srvCertPath));
            this.serverKey = readKey(Files.readString(srvKeyPath));
            log.info("802.1X 服务端证书已加载: SAN IP={}", ip);
        } else {
            KeyPair srvKp = genRsa();
            X500Name srvSubject = new X500Name("CN=NAC RADIUS 802.1X Server,O=NAC,C=CN");
            List<String> sanIps = ip == null ? Collections.emptyList() : List.of(ip);
            X500Name issuer = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());
            this.serverCert = buildCert(srvSubject, srvKp.getPublic(), issuer, caKey, false, 825, sanIps);
            this.serverKey = srvKp.getPrivate();
            Files.writeString(srvCertPath, toPem(serverCert));
            Files.writeString(srvKeyPath, toPem(serverKey));
            log.info("802.1X 服务端证书已生成: SAN IP={}", ip);
        }
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
        X509Certificate c = buildCert(subject, publicKey, issuer, caKey, false, 825, null);
        return toPem(c);
    }

    // ---- 内部工具 ----
    private KeyPair genRsa() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(RSA_BITS, new SecureRandom());
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
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        }
        if (sanIps != null && !sanIps.isEmpty()) {
            List<GeneralName> names = new ArrayList<>();
            for (String ip : sanIps) names.add(new GeneralName(GeneralName.iPAddress, ip));
            b.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names.toArray(new GeneralName[0])));
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
}
