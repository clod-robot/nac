package com.nac.auth.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nac.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程终端：浏览器 WebSocket <-> 服务器 SSH（sshj）。
 * 鉴权：仅 admin（JWT 经 url 参数 token 校验）。首条消息为连接参数 JSON，之后双向透传。
 */
@Slf4j
@Component
public class SshWebSocketHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final Map<String, SshHolder> holders = new ConcurrentHashMap<>();

    public SshWebSocketHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
        String token = queryParam(ws.getUri(), "token");
        if (token == null || !jwtUtil.validate(token)) {
            ws.close(CloseStatus.POLICY_VIOLATION.withReason("未登录或登录已过期"));
            return;
        }
        Claims c = jwtUtil.parse(token);
        if (!"admin".equals(c.get("role", String.class))) {
            ws.close(CloseStatus.POLICY_VIOLATION.withReason("仅管理员可使用远程终端"));
            return;
        }
        send(ws, "\033[36m[NAC 远程终端]\033[0m 正在建立 SSH 连接...\r\n");
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
        String payload = message.getPayload();
        try {
            SshHolder h = holders.get(ws.getId());
            if (h == null) {
                connect(ws, payload);
            } else {
                h.stdin.write(payload.getBytes(StandardCharsets.UTF_8));
                h.stdin.flush();
            }
        } catch (Exception e) {
            send(ws, "\r\n\033[31m[错误] " + e.getMessage() + "\033[0m\r\n");
            closeQuietly(ws);
        }
    }

    private void connect(WebSocketSession ws, String json) throws Exception {
        JSONObject p = JSON.parseObject(json);
        String host = p.getString("host");
        int port = p.getIntValue("port", 22);
        String username = p.getString("username");
        String password = p.getString("password");
        int cols = Math.max(p.getIntValue("cols", 80), 10);
        int rows = Math.max(p.getIntValue("rows", 24), 5);
        if (host == null || username == null || password == null) {
            send(ws, "\033[31m连接参数缺失\033[0m\r\n");
            closeQuietly(ws);
            return;
        }
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.setConnectTimeout(8000);
        ssh.setTimeout(0);
        ssh.connect(host, port);
        ssh.authPassword(username, password);
        var session = ssh.startSession();
        session.allocatePTY("xterm", cols, rows, cols * 8, rows * 16, new HashMap<>());
        var shell = session.startShell();
        OutputStream stdin = shell.getOutputStream();
        InputStream stdout = shell.getInputStream();

        SshHolder holder = new SshHolder(ssh, stdin);
        holders.put(ws.getId(), holder);
        send(ws, "\033[32mSSH 已连接 " + username + "@" + host + "\033[0m\r\n");

        Thread reader = new Thread(() -> {
            try {
                byte[] buf = new byte[4096];
                int n;
                while ((n = stdout.read(buf)) != -1) {
                    if (!ws.isOpen()) break;
                    ws.sendMessage(new TextMessage(new String(buf, 0, n, StandardCharsets.UTF_8)));
                }
            } catch (Exception ignore) {
            } finally {
                closeQuietly(ws);
            }
        }, "ssh-reader-" + ws.getId());
        reader.setDaemon(true);
        holder.reader = reader;
        reader.start();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        cleanup(ws.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession ws, Throwable exception) {
        cleanup(ws.getId());
    }

    private void cleanup(String id) {
        SshHolder h = holders.remove(id);
        if (h == null) return;
        try { if (h.reader != null) h.reader.interrupt(); } catch (Exception ignore) {}
        try { h.ssh.disconnect(); } catch (Exception ignore) {}
    }

    private static String queryParam(URI uri, String key) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String kv : uri.getQuery().split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(key)) return kv.substring(i + 1);
        }
        return null;
    }

    private void send(WebSocketSession ws, String text) {
        try { if (ws.isOpen()) ws.sendMessage(new TextMessage(text)); } catch (Exception ignore) {}
    }

    private void closeQuietly(WebSocketSession ws) {
        try { if (ws.isOpen()) ws.close(); } catch (Exception ignore) {}
    }

    private static class SshHolder {
        final SSHClient ssh;
        final OutputStream stdin;
        Thread reader;
        SshHolder(SSHClient ssh, OutputStream stdin) { this.ssh = ssh; this.stdin = stdin; }
    }
}
