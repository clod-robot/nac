package com.nac.auth.service;

import com.alibaba.fastjson2.JSON;
import com.nac.auth.dto.MonitorSnapshot;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.NetworkIF;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务器监控采集（CPU/内存/网卡）+ 微服务存活探测。
 * 后台每 2s 采集一次并通过 SSE 推送给在线仪表盘；GET /snapshot 返回全量快照。
 */
@Slf4j
@Service
public class MonitorService {

    private final SystemInfo si = new SystemInfo();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "monitor-collector");
        t.setDaemon(true);
        return t;
    });
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    private volatile MonitorSnapshot latest = new MonitorSnapshot();

    private long[] prevTicks;
    private final Map<String, long[]> prevNet = new HashMap<>(); // name -> [rx, tx]
    private long prevTs = 0L;
    private volatile Integer memFreq;
    private volatile boolean freqTried;

    /** 微服务/中间件存活探测（按本地端口） */
    private static final Object[][] SERVICES = {
            {"Nginx", 443}, {"Redis", 6379}, {"Kafka", 9092}, {"MySQL", 3306},
            {"网关 gateway", 8080}, {"认证 auth", 8081}, {"用户 user", 8082},
            {"RADIUS", 8083}, {"日志 log", 8084}
    };

    public MonitorService() {
        collect();
        scheduler.scheduleAtFixedRate(this::collectSafe, 2, 2, TimeUnit.SECONDS);
    }

    private void collectSafe() {
        try {
            collect();
        } catch (Exception e) {
            log.warn("监控采集异常: {}", e.getMessage());
        }
    }

    private synchronized void collect() {
        CentralProcessor cpu = si.getHardware().getProcessor();
        long[] ticks = cpu.getSystemCpuLoadTicks();
        double cpuUsage = 0;
        if (prevTicks != null) {
            double d = cpu.getSystemCpuLoadBetweenTicks(prevTicks);
            if (!Double.isNaN(d)) cpuUsage = d * 100;
        }
        prevTicks = ticks;

        GlobalMemory mem = si.getHardware().getMemory();
        long total = mem.getTotal();
        long avail = mem.getAvailable();
        long used = Math.max(0, total - avail);
        double memUsage = total > 0 ? used * 100.0 / total : 0;

        long now = System.currentTimeMillis();
        double interval = prevTs > 0 ? (now - prevTs) / 1000.0 : 2.0;
        if (interval <= 0) interval = 2.0;
        prevTs = now;

        List<NetworkIF> nets = si.getHardware().getNetworkIFs();
        List<MonitorSnapshot.Net> netList = new ArrayList<>();
        for (NetworkIF n : nets) {
            n.updateAttributes();
            long rx = n.getBytesRecv();
            long tx = n.getBytesSent();
            long[] p = prevNet.get(n.getName());
            long rxRate = p != null ? Math.max(0, (long) ((rx - p[0]) / interval)) : 0;
            long txRate = p != null ? Math.max(0, (long) ((tx - p[1]) / interval)) : 0;
            prevNet.put(n.getName(), new long[]{rx, tx});

            MonitorSnapshot.Net ni = new MonitorSnapshot.Net();
            ni.setName(n.getName());
            ni.setDisplayName(n.getDisplayName());
            ni.setUp(n.getIfOperStatus() == NetworkIF.IfOperStatus.UP);
            ni.setSpeedMbps(n.getSpeed() > 0 ? n.getSpeed() / 1_000_000L : 0);
            ni.setRxRateBps(rxRate);
            ni.setTxRateBps(txRate);
            netList.add(ni);
        }

        MonitorSnapshot s = new MonitorSnapshot();
        MonitorSnapshot.Cpu c = new MonitorSnapshot.Cpu();
        c.setModel(trim(cpu.getProcessorIdentifier().getName()));
        c.setPhysicalCores(cpu.getPhysicalProcessorCount());
        c.setLogicalCores(cpu.getLogicalProcessorCount());
        c.setUsage(round(cpuUsage));
        s.setCpu(c);

        MonitorSnapshot.Memory m = new MonitorSnapshot.Memory();
        m.setTotalBytes(total);
        m.setUsedBytes(used);
        m.setAvailableBytes(avail);
        m.setUsagePercent(round(memUsage));
        m.setFrequencyMHz(memoryFrequency());
        s.setMemory(m);

        s.setNetwork(netList);
        this.latest = s;

        pushLive();
    }

    private void pushLive() {
        if (emitters.isEmpty()) return;
        String json = liveJson();
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name("metrics").data(json));
            } catch (Exception ex) {
                emitters.remove(e);
                try { e.complete(); } catch (Exception ignore) { }
            }
        }
    }

    private String liveJson() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cpu", latest.getCpu() == null ? 0 : latest.getCpu().getUsage());
        data.put("mem", latest.getMemory() == null ? 0 : latest.getMemory().getUsagePercent());
        data.put("ts", System.currentTimeMillis());
        List<Map<String, Object>> nets = new ArrayList<>();
        if (latest.getNetwork() != null) {
            for (MonitorSnapshot.Net n : latest.getNetwork()) {
                Map<String, Object> nm = new LinkedHashMap<>();
                nm.put("name", n.getName());
                nm.put("rx", n.getRxRateBps());
                nm.put("tx", n.getTxRateBps());
                nets.add(nm);
            }
        }
        data.put("net", nets);
        return JSON.toJSONString(data);
    }

    /** 全量快照（含服务状态） */
    public synchronized MonitorSnapshot snapshot() {
        MonitorSnapshot s = new MonitorSnapshot();
        s.setCpu(latest.getCpu());
        s.setMemory(latest.getMemory());
        s.setNetwork(latest.getNetwork() == null ? Collections.emptyList() : new ArrayList<>(latest.getNetwork()));
        s.setServices(checkServices());
        return s;
    }

    private List<MonitorSnapshot.Svc> checkServices() {
        List<MonitorSnapshot.Svc> list = new ArrayList<>();
        for (Object[] svc : SERVICES) {
            MonitorSnapshot.Svc s = new MonitorSnapshot.Svc();
            s.setName((String) svc[0]);
            s.setPort((Integer) svc[1]);
            s.setOnline(portOpen((Integer) svc[1]));
            list.add(s);
        }
        return list;
    }

    private boolean portOpen(int port) {
        try (Socket sk = new Socket()) {
            sk.connect(new InetSocketAddress("127.0.0.1", port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 内存频率：尽力读取 dmidecode（需 root，无权限返回 null，前端显示“—”） */
    private Integer memoryFrequency() {
        if (freqTried) return memFreq;
        freqTried = true;
        try {
            Process p = new ProcessBuilder("sh", "-c",
                    "dmidecode -t memory 2>/dev/null | grep -i -m1 'Configured Clock Speed'").start();
            String out = new String(p.getInputStream().readAllBytes());
            Matcher mt = Pattern.compile("(\\d+)").matcher(out);
            if (mt.find()) memFreq = Integer.parseInt(mt.group(1));
            p.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 无权限：忽略
        }
        return memFreq;
    }

    /** SSE 订阅 */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> { emitters.remove(emitter); emitter.complete(); });
        emitter.onError(t -> { emitters.remove(emitter); emitter.complete(); });
        try {
            emitter.send(SseEmitter.event().name("metrics").data(liveJson()));
        } catch (Exception ignore) { }
        return emitter;
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdownNow();
        for (SseEmitter e : emitters) {
            try { e.complete(); } catch (Exception ignore) { }
        }
    }

    private static double round(double v) { return Math.round(v * 10) / 10.0; }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
