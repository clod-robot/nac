package com.nac.auth.service;

import com.nac.common.config.JacksonConfig;
import com.nac.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务器系统信息：当前时间、时区、NTP 服务器读取与修改。
 * NTP 由 systemd-timesyncd 管理，修改通过受限 sudo 脚本 /usr/local/sbin/nac-set-ntp.sh 完成。
 */
@Slf4j
@Service
public class SystemService {

    private static final Pattern SERVER_LINE = Pattern.compile("Server:\\s*(.+)");
    /** 仅允许域名/IP，含可选空格分隔的说明，禁止 shell 元字符。 */
    private static final Pattern NTP_VALID = Pattern.compile("^[A-Za-z0-9.:\\-\\s]+$");
    private static final String SET_NTP_SCRIPT = "/usr/local/sbin/nac-set-ntp.sh";

    public SystemInfo info() {
        SystemInfo i = new SystemInfo();
        i.setServerTime(JacksonConfig.now());
        i.setEpochMs(System.currentTimeMillis());
        i.setTimeZone(readTimeZone());
        i.setNtpServer(readNtpServer());
        return i;
    }

    public void setNtp(String ntp) {
        if (ntp == null) throw new BusinessException(400, "NTP 地址不能为空");
        String v = ntp.trim();
        if (v.isEmpty() || !NTP_VALID.matcher(v).matches() || v.length() > 128)
            throw new BusinessException(400, "NTP 地址格式非法");
        // 以首个 token 作为真实服务器地址写入
        String host = v.split("\\s+")[0];
        try {
            run("sudo", "-n", SET_NTP_SCRIPT, host);
            log.info("NTP 服务器已更新为: {}", host);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("设置 NTP 失败", e);
            throw new BusinessException(400, "设置 NTP 失败: " + e.getMessage());
        }
    }

    private String readNtpServer() {
        // 1) 静态配置
        try {
            String out = run("/usr/bin/timedatectl", "show", "-p", "NTPServers", "--value");
            String s = out.trim();
            if (!s.isEmpty()) return s;
        } catch (Exception ignore) {}
        // 2) 当前实际同步的服务器
        try {
            String out = run("/usr/bin/timedatectl", "timesync-status");
            Matcher m = SERVER_LINE.matcher(out);
            if (m.find()) return m.group(1).trim();
        } catch (Exception ignore) {}
        return "(未配置)";
    }

    private String readTimeZone() {
        try {
            return run("/usr/bin/timedatectl", "show", "-p", "Timezone", "--value").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
        }
        p.waitFor();
        if (p.exitValue() != 0) throw new IllegalStateException(sb.toString().trim());
        return sb.toString();
    }

    @lombok.Data
    public static class SystemInfo {
        private String serverTime;
        private Long epochMs;
        private String timeZone;
        private String ntpServer;
    }
}
