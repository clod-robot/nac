#ifndef SCANTYPES_H
#define SCANTYPES_H

#include <QString>
#include <QStringList>
#include <QVector>
#include <QMap>
#include <QMetaType>
#include <QDateTime>

// 漏洞风险等级
enum Severity {
    SevInfo = 0,
    SevLow = 1,
    SevMedium = 2,
    SevHigh = 3,
    SevCritical = 4
};

static inline QString severityText(int s)
{
    switch (s) {
    case SevCritical: return QStringLiteral("严重");
    case SevHigh:     return QStringLiteral("高危");
    case SevMedium:   return QStringLiteral("中危");
    case SevLow:      return QStringLiteral("低危");
    default:          return QStringLiteral("信息");
    }
}

// 单条漏洞命中
struct VulnFinding {
    QString ruleId;        // CVE 编号或规则编号
    QString title;         // 漏洞名称
    int severity = SevInfo;
    QString product;       // 受影响产品
    QString version;       // 探测到的版本
    QString description;   // 描述
    QString solution;      // 修复建议
    QString reference;     // 参考链接
};

// 单个端口的扫描结果
struct PortResult {
    QString host;
    quint16 port = 0;
    bool open = false;
    int latencyMs = -1;
    QString serviceHint;   // 端口表推断的服务名
    QString service;       // 实际识别出的服务
    QString product;       // 产品，如 OpenSSH / nginx
    QString version;       // 版本
    QString banner;        // 原始 Banner（截断）
    QString httpTitle;     // HTTP 页面标题
    QString httpServer;    // Server 头
    QString httpPoweredBy; // X-Powered-By 头
    QString httpBody;      // 响应体片段（约 4KB）
    QMap<QString, QString> httpHeaders;
    int httpStatus = 0;
    bool tls = false;
    bool unauth = false;   // 存在未授权访问（Redis/ES/Docker 等）
    QVector<VulnFinding> findings;
};

// 一台主机的汇总
struct HostResult {
    QString address;       // IP
    QString hostname;      // 解析输入的主机名（如有）
    bool up = false;
    QVector<PortResult> ports;
};

// 扫描汇总统计
struct ScanSummary {
    int hostsTotal = 0;
    int hostsUp = 0;
    int openPorts = 0;
    int critical = 0;
    int high = 0;
    int medium = 0;
    int low = 0;
    int info = 0;
    qint64 elapsedMs = 0;
};

Q_DECLARE_METATYPE(PortResult)
Q_DECLARE_METATYPE(VulnFinding)
Q_DECLARE_METATYPE(ScanSummary)

#endif // SCANTYPES_H
