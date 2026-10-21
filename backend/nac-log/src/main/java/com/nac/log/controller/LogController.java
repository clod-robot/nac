package com.nac.log.controller;

import com.nac.common.constant.RedisKeyConstants;
import com.nac.common.log.OperationLog;
import com.nac.common.redis.RedisUtil;
import com.nac.common.result.Result;
import com.nac.common.security.RequireRole;
import com.nac.log.entity.AuthLog;
import com.nac.log.entity.OnlineSession;
import com.nac.log.entity.SysLog;
import com.nac.log.entity.SysUserBrief;
import com.nac.log.mapper.AuthLogMapper;
import com.nac.log.mapper.OnlineSessionMapper;
import com.nac.log.mapper.SysLogMapper;
import com.nac.log.mapper.SysUserBriefMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志查询（仅管理员）：操作日志、认证日志、在线会话。删除接口不对外。
 */
@RestController
@RequestMapping("/api/log")
@RequireRole("admin")
public class LogController {

    private final SysLogMapper sysLogMapper;
    private final AuthLogMapper authLogMapper;
    private final OnlineSessionMapper onlineSessionMapper;
    private final SysUserBriefMapper sysUserBriefMapper;
    private final RedisUtil redisUtil;

    public LogController(SysLogMapper sysLogMapper, AuthLogMapper authLogMapper,
                         OnlineSessionMapper onlineSessionMapper, SysUserBriefMapper sysUserBriefMapper,
                         RedisUtil redisUtil) {
        this.sysLogMapper = sysLogMapper;
        this.authLogMapper = authLogMapper;
        this.onlineSessionMapper = onlineSessionMapper;
        this.sysUserBriefMapper = sysUserBriefMapper;
        this.redisUtil = redisUtil;
    }

    @GetMapping("/list")
    @RequireRole("admin")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String operation,
                                            @RequestParam(required = false) String startTime,
                                            @RequestParam(required = false) String endTime) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        int offset = (page - 1) * size;
        String op = trim(operation), st = trim(startTime), et = trim(endTime);
        List<SysLog> list = sysLogMapper.selectPage(offset, size, op, st, et);
        // 带筛选条件时按条件统计，否则走缓存总数
        long total = (op != null || st != null || et != null)
                ? sysLogMapper.countAll(op, st, et) : countWithCache();
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.success(data);
    }

    /** 认证日志（login/portal/radius），支持按类型、结果、用户、MAC、终端IP、时间范围筛选 */
    @GetMapping("/auth-list")
    @RequireRole("admin")
    public Result<Map<String, Object>> authList(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) Integer result,
                                                @RequestParam(required = false) String username,
                                                @RequestParam(required = false) String mac,
                                                @RequestParam(required = false) String ip,
                                                @RequestParam(required = false) String startTime,
                                                @RequestParam(required = false) String endTime) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        int offset = (page - 1) * size;
        String st = trim(startTime);
        String et = trim(endTime);
        // 前端精确到分(yyyy-MM-dd HH:mm)，结束时间补秒以覆盖该分钟内全部记录
        if (et != null && et.length() == 16) et = et + ":59";
        List<AuthLog> list = authLogMapper.selectPage(offset, size, type, result, trim(username), trim(mac), trim(ip), st, et);
        long total = authLogMapper.countAll(type, result, trim(username), trim(mac), trim(ip), st, et);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.success(data);
    }

    /** 在线会话（RADIUS 计费驱动的在线终端），支持按用户、MAC、终端IP筛选 */
    @GetMapping("/online-list")
    @RequireRole("admin")
    public Result<Map<String, Object>> onlineList(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String username,
                                                  @RequestParam(required = false) String mac,
                                                  @RequestParam(required = false) String ip) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        int offset = (page - 1) * size;
        List<OnlineSession> list = onlineSessionMapper.selectPage(offset, size, trim(username), trim(mac), trim(ip));
        long total = onlineSessionMapper.countAll(trim(username), trim(mac), trim(ip));
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.success(data);
    }

    private static String trim(String s) {
        return s == null ? null : (s.trim().isEmpty() ? null : s.trim());
    }

    /**
     * 安全事件溯源 - 统计分析
     * 返回指定时间窗内的认证趋势、Top 失败终端/用户/NAS、SSID 分布、认证类型分布。
     * 时间参数缺省时默认近 24 小时。
     */
    @GetMapping("/trace/stats")
    @RequireRole("admin")
    public Result<Map<String, Object>> traceStats(@RequestParam(required = false) String startTime,
                                                  @RequestParam(required = false) String endTime,
                                                  @RequestParam(defaultValue = "10") int topN) {
        String st = trim(startTime);
        String et = trim(endTime);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (et == null) et = LocalDateTime.now().format(fmt);
        if (st == null) st = LocalDateTime.now().minusHours(24).format(fmt);
        // 前端 datetime 控件精确到分时，结束时间补秒以覆盖该分钟
        if (et.length() == 16) et = et + ":59";
        if (st.length() == 16) st = st + ":00";
        int limit = Math.min(Math.max(topN, 1), 50);

        Map<String, Object> data = new HashMap<>();
        data.put("startTime", st);
        data.put("endTime", et);
        data.put("trend", authLogMapper.statTrendByHour(st, et));
        data.put("topFailMac", authLogMapper.statTopFailMac(st, et, limit));
        data.put("topFailUser", authLogMapper.statTopFailUser(st, et, limit));
        data.put("topFailNas", authLogMapper.statTopFailNas(st, et, limit));
        data.put("ssidDist", authLogMapper.statSsidDist(st, et));
        data.put("authTypeDist", authLogMapper.statAuthTypeDist(st, et));
        return Result.success(data);
    }

    /**
     * 安全事件溯源 - 责任人反查
     * 优先按 username 精确匹配；若 username 为空（如 portal 短信认证），回退按 phone 匹配。
     * 返回最小必要字段，不包含密文。
     */
    @GetMapping("/trace/user")
    @RequireRole("admin")
    public Result<Map<String, Object>> traceUser(@RequestParam(required = false) String username,
                                                 @RequestParam(required = false) String phone) {
        String un = trim(username);
        String ph = trim(phone);
        SysUserBrief user = null;
        if (un != null) {
            user = sysUserBriefMapper.selectByUsername(un);
        }
        if (user == null && ph != null) {
            user = sysUserBriefMapper.selectByPhone(ph);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("found", user != null);
        data.put("user", user);
        return Result.success(data);
    }

    /**
     * 安全事件溯源 - 导出 CSV（公安电子证据留存）
     * 复用 auth-list 的筛选条件，LEFT JOIN sys_user 附带责任人姓名/部门，
     * UTF-8 带 BOM，Excel 直接打开不乱码；单次最多 50000 行。
     */
    @GetMapping("/trace/export")
    @RequireRole("admin")
    @OperationLog("导出安全事件溯源 CSV")
    public void exportTrace(@RequestParam(required = false) String type,
                            @RequestParam(required = false) Integer result,
                            @RequestParam(required = false) String username,
                            @RequestParam(required = false) String mac,
                            @RequestParam(required = false) String ip,
                            @RequestParam(required = false) String startTime,
                            @RequestParam(required = false) String endTime,
                            HttpServletResponse response) throws IOException {
        String st = trim(startTime);
        String et = trim(endTime);
        if (et != null && et.length() == 16) et = et + ":59";
        if (st != null && st.length() == 16) st = st + ":00";
        List<Map<String, Object>> rows = authLogMapper.selectForExport(
                trim(type), result, trim(username), trim(mac), trim(ip), st, et, 50000);

        String filename = "SecurityTrace_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"");
        PrintWriter w = response.getWriter();
        // UTF-8 BOM：让 Excel 直接识别为 UTF-8，中文表头/内容不乱码
        w.write(0xFEFF);
        w.println("序号,时间,认证类型,账号,手机号,MAC,终端IP,NAS IP,NAS 名称,SSID,结果,说明,责任人姓名,责任人账号,责任人部门");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int idx = 1;
        for (Map<String, Object> r : rows) {
            Object ct = r.get("createTime");
            String timeStr = (ct instanceof LocalDateTime) ? ((LocalDateTime) ct).format(fmt) : String.valueOf(ct == null ? "" : ct);
            Integer res = r.get("result") instanceof Number ? ((Number) r.get("result")).intValue() : null;
            StringBuilder sb = new StringBuilder();
            sb.append(idx++);
            sb.append(',').append(csv(timeStr));
            sb.append(',').append(csv(str(r.get("authType"))));
            sb.append(',').append(csv(str(r.get("username"))));
            sb.append(',').append(csv(str(r.get("phone"))));
            sb.append(',').append(csv(str(r.get("mac"))));
            sb.append(',').append(csv(str(r.get("ip"))));
            sb.append(',').append(csv(str(r.get("nasIp"))));
            sb.append(',').append(csv(str(r.get("nasName"))));
            sb.append(',').append(csv(str(r.get("ssid"))));
            sb.append(',').append(res == null ? "" : (res == 1 ? "成功" : "失败"));
            sb.append(',').append(csv(str(r.get("message"))));
            sb.append(',').append(csv(str(r.get("respName"))));
            sb.append(',').append(csv(str(r.get("respUsername"))));
            sb.append(',').append(csv(str(r.get("respDept"))));
            w.println(sb);
        }
        w.flush();
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    /** CSV 字段转义：含逗号/引号/换行时用双引号包裹，内嵌双引号双写 */
    private static String csv(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    /** 强制下线（删除在线会话记录） */
    @DeleteMapping("/online/{id}")
    @RequireRole("admin")
    @OperationLog("强制下线终端")
    public Result<Void> offline(@PathVariable("id") Long id) {
        onlineSessionMapper.deleteById(id);
        return Result.success();
    }

    private long countWithCache() {
        try {
            String cached = redisUtil.get(RedisKeyConstants.LOG_COUNT_ALL);
            if (cached != null) return Long.parseLong(cached);
            long total = sysLogMapper.countAll(null, null, null);
            redisUtil.set(RedisKeyConstants.LOG_COUNT_ALL, String.valueOf(total), 60);
            return total;
        } catch (Exception e) {
            return sysLogMapper.countAll(null, null, null);
        }
    }
}
