package com.nac.radius.eap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * EAP-TLS/PEAP 传输层（RFC 5216）：用 SSLEngine 在 RADIUS EAP-Message 之上承载 TLS。
 * 会话按随机 sessionId（置于 State 属性）维持，跨 RADIUS 往返保存 SSLEngine 与分片重组缓冲。
 */
@Slf4j
@Component
public class EapTlsSupport {

    /** 单个 EAP-TLS 报文承载的最大 TLS 字节数（RADIUS 属性单字节长度上限 255，需留余量）。 */
    private static final int FRAGMENT = 240;
    private static final long SESSION_TTL_MS = 60_000;

    private final SSLContext sslContext;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "radius-tls-task");
        t.setDaemon(true);
        return t;
    });

    public EapTlsSupport(CertManager certManager) throws Exception {
        this.sslContext = certManager.serverSslContext();
    }

    /** 一次处理的结果：outgoingTls 为需下发的 TLS 字节；appData 为握手完成后解出的明文（PEAP 内层 EAP）。 */
    public record StepResult(byte[] outgoingTls, boolean needFragment, boolean success, boolean failed,
                             String reason, byte[] appData) {}

    public static class Session {
        final SSLEngine engine;
        final ByteArrayOutputStream inBuf = new ByteArrayOutputStream(); // 入站分片重组
        public final java.util.ArrayDeque<byte[]> outFrags = new java.util.ArrayDeque<>(); // 出站分片队列
        boolean clientAuth;
        public boolean handshakeDone;
        public boolean peapInnerStarted;   // PEAP：内层 EAP 是否已发起
        public int innerId;                // PEAP：内层 EAP 当前 id
        public byte[] innerChallenge;      // PEAP：内层 MD5 challenge
        long lastSeen = System.currentTimeMillis();
        Session(SSLEngine engine, boolean clientAuth) { this.engine = engine; this.clientAuth = clientAuth; }
    }

    public String newSession(boolean needClientAuth) throws SSLException {
        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(needClientAuth);
        Session s = new Session(engine, needClientAuth);
        String id = randomId();
        sessions.put(id, s);
        return id;
    }

    /** 处理一段入站 EAP-TLS 数据（已重组后的完整 TLS 载荷由调用方保证传入完整帧）。 */
    public StepResult step(String sessionId, byte[] tlsPayload) {
        Session s = sessions.get(sessionId);
        if (s == null) return new StepResult(null, false, false, true, "no session", null);
        s.lastSeen = System.currentTimeMillis();
        try {
            SSLEngine engine = s.engine;
            // 1) 喂入网络数据
            byte[] appData = null;
            if (tlsPayload != null && tlsPayload.length > 0) {
                ByteBuffer netIn = ByteBuffer.wrap(tlsPayload);
                ByteArrayOutputStream app = new ByteArrayOutputStream();
                while (netIn.hasRemaining()) {
                    ByteBuffer appBuf = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
                    SSLEngineResult r = engine.unwrap(netIn, appBuf);
                    runTasks(engine);
                    if (r.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) s.handshakeDone = true;
                    appBuf.flip();
                    if (appBuf.hasRemaining()) {
                        byte[] a = new byte[appBuf.remaining()];
                        appBuf.get(a);
                        app.writeBytes(a);
                    }
                    if (r.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) break;
                    if (r.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) break;
                }
                if (app.size() > 0) appData = app.toByteArray();
            }
            // 2) 握手未完成则 wrap 产出需发送的握手数据
            if (!s.handshakeDone) {
                runTasks(engine);
                ByteBuffer netOut = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
                SSLEngineResult r = engine.wrap(ByteBuffer.allocate(0), netOut);
                runTasks(engine);
                if (r.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) s.handshakeDone = true;
                netOut.flip();
                byte[] out = new byte[netOut.remaining()];
                netOut.get(out);
                if (s.handshakeDone) {
                    if (s.clientAuth && !hasPeerCert(engine)) {
                        return new StepResult(null, false, false, true, "no client cert", null);
                    }
                    return new StepResult(out, out.length > FRAGMENT, true, false, null, null);
                }
                return new StepResult(out, out.length > FRAGMENT, false, false, null, null);
            }
            // 3) 握手已完成：返回解出的应用明文（PEAP 内层 EAP）；EAP-TLS 终点由 success 上一轮处理
            return new StepResult(null, false, false, false, null, appData);
        } catch (Exception e) {
            sessions.remove(sessionId);
            log.warn("EAP-TLS 处理失败 session={}: {}", sessionId, e.getMessage());
            return new StepResult(null, false, false, true, e.getMessage(), null);
        }
    }

    /** 握手完成后，将明文（内层 EAP）封装为 TLS 网络字节，供分片下发。 */
    public byte[] wrapApp(String sessionId, byte[] appBytes) throws SSLException {
        Session s = sessions.get(sessionId);
        if (s == null) throw new SSLException("no session");
        s.lastSeen = System.currentTimeMillis();
        ByteBuffer netOut = ByteBuffer.allocate(s.engine.getSession().getPacketBufferSize());
        s.engine.wrap(ByteBuffer.wrap(appBytes), netOut);
        netOut.flip();
        byte[] out = new byte[netOut.remaining()];
        netOut.get(out);
        return out;
    }

    private boolean hasPeerCert(SSLEngine engine) {
        try { return engine.getSession().getPeerCertificates() != null; }
        catch (Exception e) { return false; }
    }

    /** 构造 EAP-TLS 数据字段：flags[+len]+payload，按 FRAGMENT 分片。返回 EAP data（不含 EAP 头）列表。 */
    public byte[][] fragmentForEap(byte[] tls) {
        if (tls == null) tls = new byte[0];
        if (tls.length <= FRAGMENT) {
            return new byte[][]{eapTlsData(tls, false, false, false)};
        }
        int n = (tls.length + FRAGMENT - 1) / FRAGMENT;
        byte[][] out = new byte[n][];
        for (int i = 0; i < n; i++) {
            int from = i * FRAGMENT;
            int len = Math.min(FRAGMENT, tls.length - from);
            byte[] part = new byte[len];
            System.arraycopy(tls, from, part, 0, len);
            boolean more = i < n - 1;
            boolean start = i == 0;
            out[i] = eapTlsData(part, more, start, true);
        }
        return out;
    }

    /** flags: S(0x20) M(0x40) L(0x80)。L=1 时附带 4 字节总长度。 */
    private byte[] eapTlsData(byte[] payload, boolean moreFragments, boolean start, boolean includeLength) {
        int flags = 0;
        if (start) flags |= 0x20;
        if (moreFragments) flags |= 0x40;
        if (includeLength) flags |= 0x80;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(flags);
        if (includeLength) {
            int total = payload.length; // 简化：每片写入本片长度（兼容多数实现对长度字段的宽松处理）
            o.write((total >>> 24) & 0xFF); o.write((total >>> 16) & 0xFF);
            o.write((total >>> 8) & 0xFF); o.write(total & 0xFF);
        }
        o.writeBytes(payload);
        return o.toByteArray();
    }

    /**
     * 解析入站 EAP-TLS data：累积分片到会话缓冲。M 标志置位表示还有后续分片，返回 null；
     * 否则返回已重组的完整 TLS 载荷（可能为空，如 TLS ACK）。
     */
    public byte[] accumulate(Session s, byte[] eapData) {
        if (eapData == null || eapData.length < 1) return null;
        int flags = eapData[0] & 0xFF;
        int off = 1;
        if ((flags & 0x80) != 0) off += 4; // 跳过 L 长度字段
        if (eapData.length > off) {
            byte[] frag = new byte[eapData.length - off];
            System.arraycopy(eapData, off, frag, 0, frag.length);
            s.inBuf.writeBytes(frag);
        }
        if ((flags & 0x40) != 0) return null; // 还有分片
        byte[] full = s.inBuf.toByteArray();
        s.inBuf.reset();
        return full;
    }

    private void runTasks(SSLEngine engine) {
        Runnable r;
        while ((r = engine.getDelegatedTask()) != null) {
            try { taskExecutor.submit(r).get(5, TimeUnit.SECONDS); }
            catch (Exception e) { throw new IllegalStateException("TLS task failed", e); }
        }
    }

    public Session get(String id) {
        Session s = sessions.get(id);
        if (s != null && System.currentTimeMillis() - s.lastSeen > SESSION_TTL_MS) {
            sessions.remove(id);
            return null;
        }
        return s;
    }

    public void remove(String id) { sessions.remove(id); }

    private static String randomId() {
        byte[] b = new byte[18];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
