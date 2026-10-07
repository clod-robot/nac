package com.nac.auth.dto;

import lombok.Data;
import java.util.List;

/** 仪表盘服务器监控快照（GET 一次性返回）。 */
@Data
public class MonitorSnapshot {
    private Cpu cpu;
    private Memory memory;
    private List<Net> network;
    private List<Svc> services;

    @Data
    public static class Cpu {
        private String model;          // CPU 型号
        private int physicalCores;     // 物理核数
        private int logicalCores;      // 逻辑核数
        private double usage;          // 使用率 0-100
    }

    @Data
    public static class Memory {
        private long totalBytes;       // 总内存
        private long usedBytes;        // 已用
        private long availableBytes;   // 可用
        private double usagePercent;   // 使用率 0-100
        private Integer frequencyMHz;  // 内存频率（无权限读取时为 null）
    }

    @Data
    public static class Net {
        private String name;           // 接口名
        private String displayName;    // 显示名
        private boolean up;            // 链路状态
        private long speedMbps;        // 协商速率（0=未知）
        private long rxRateBps;        // 下载速率（字节/秒）
        private long txRateBps;        // 上传速率（字节/秒）
    }

    @Data
    public static class Svc {
        private String name;
        private int port;
        private boolean online;
    }
}
