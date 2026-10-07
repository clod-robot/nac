package com.nac.common.xss;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.util.HtmlUtils;

/**
 * XSS 防护请求包装：对参数与请求头做 HTML 转义。主防线为安全响应头 + 前端默认转义，此为二次过滤。
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String v = super.getParameter(name);
        return v == null ? null : HtmlUtils.htmlEscape(v);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] arr = super.getParameterValues(name);
        if (arr == null) return null;
        String[] out = new String[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i] == null ? null : HtmlUtils.htmlEscape(arr[i]);
        }
        return out;
    }

    @Override
    public String getHeader(String name) {
        String v = super.getHeader(name);
        return v == null ? null : HtmlUtils.htmlEscape(v);
    }
}
