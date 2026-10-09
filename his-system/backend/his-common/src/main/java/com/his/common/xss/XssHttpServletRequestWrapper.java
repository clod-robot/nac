package com.his.common.xss;

import cn.hutool.http.HtmlUtil;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * XSS 防护请求包装器：对参数和 JSON body 进行 HTML 转义。
 * 前端 Vue 插值默认已转义，后端二次过滤形成纵深防御。
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        return escape(super.getParameter(name));
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) return null;
        String[] escaped = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            escaped[i] = escape(values[i]);
        }
        return escaped;
    }

    @Override
    public String getHeader(String name) {
        return escape(super.getHeader(name));
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        String body = readBody(super.getInputStream());
        // 对 JSON body 中的字符串值做转义（简单策略：整体转义 < > & " ' 等危险字符边界）
        String escaped = escapeJsonBody(body);
        ByteArrayInputStream bis = new ByteArrayInputStream(escaped.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override
            public boolean isFinished() { return bis.available() == 0; }
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setReadListener(ReadListener readListener) {}
            @Override
            public int read() { return bis.read(); }
        };
    }

    private String readBody(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int len;
        while ((len = is.read(buf)) != -1) bos.write(buf, 0, len);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private String escapeJsonBody(String body) {
        if (body == null || body.isEmpty()) return body;
        // 仅转义可能注入事件/标签的字符，避免破坏 JSON 结构
        return body.replace("<", "\\u003c").replace(">", "\\u003e");
    }

    private String escape(String value) {
        return value == null ? null : HtmlUtil.escape(value);
    }
}
