#include "scanengine.h"
#include "porttask.h"
#include "servicedb.h"

#include <QThreadPool>
#include <QElapsedTimer>
#include <QSet>
#include <QHash>

ScanEngine::ScanEngine(const VulnDb *db, QObject *parent)
    : QObject(parent)
{
    m_ctx.db = db;
}

ScanEngine::~ScanEngine() {}

void ScanEngine::configure(const QString &targetInput, const QVector<quint16> &ports,
                           const ScanOptions &opts)
{
    m_targetInput = targetInput;
    m_ports = ports;
    m_opts = opts;
}

void ScanEngine::cancel()
{
    m_ctx.cancel.storeRelease(1);
}

void ScanEngine::start()
{
    QElapsedTimer overall;
    overall.start();

    m_ctx.opts = m_opts;
    m_ctx.cancel.storeRelease(0);
    m_ctx.doneCount.storeRelease(0);

    ScanSummary summary;

    // 1) 解析目标
    QStringList parseErrs;
    QVector<ScanTarget> targets = TargetParser::parse(m_targetInput, &parseErrs);
    for (const QString &e : parseErrs)
        emit log(QStringLiteral("[错误] ") + e);
    if (targets.isEmpty()) {
        emit log(QStringLiteral("[错误] 没有有效扫描目标"));
        emit finished(summary);
        return;
    }
    if (m_ports.isEmpty()) {
        emit log(QStringLiteral("[错误] 端口列表为空"));
        emit finished(summary);
        return;
    }

    // 2) 域名解析（在 worker 线程中阻塞执行）
    QVector<ScanTarget> resolved;
    QHash<QString, QString> labelOf;
    for (const ScanTarget &t : targets) {
        if (m_ctx.cancel.loadAcquire()) break;
        QString ip = TargetParser::resolveHostname(t.address);
        if (ip.isEmpty()) {
            emit log(QStringLiteral("[跳过] 域名解析失败: %1").arg(t.label));
            continue;
        }
        if (!labelOf.contains(ip)) {
            resolved.append({ip, t.label});
            labelOf.insert(ip, t.label != ip ? t.label : ip);
        }
    }
    summary.hostsTotal = resolved.size();
    emit hostsEnumerated(resolved.size());
    emit log(QStringLiteral("目标主机 %1 台，每台端口 %2 个，共 %3 个探测任务")
                 .arg(resolved.size())
                 .arg(m_ports.size())
                 .arg((qulonglong)resolved.size() * m_ports.size()));

    if (resolved.isEmpty()) {
        emit finished(summary);
        return;
    }

    // 3) 主机存活探测
    QVector<ScanTarget> scanList;
    if (m_opts.hostDiscovery) {
        emit log(QStringLiteral("正在进行主机存活探测 (TCP Ping)..."));
        QThreadPool pingPool;
        pingPool.setMaxThreadCount(qMax(1, m_opts.concurrency / 2));
        for (const ScanTarget &t : resolved)
            pingPool.start(new HostPingTask(t.address, t.label, &m_ctx));
        pingPool.waitForDone();
        for (const ScanTarget &t : resolved) {
            if (m_ctx.aliveHosts.contains(t.address)) {
                scanList.append(t);
                emit hostUp(t.address, labelOf.value(t.address, t.address));
            }
        }
        emit log(QStringLiteral("存活主机 %1 / %2")
                     .arg(scanList.size()).arg(resolved.size()));
    } else {
        scanList = resolved;
        for (const ScanTarget &t : scanList)
            emit hostUp(t.address, labelOf.value(t.address, t.address));
    }

    if (scanList.isEmpty() || m_ctx.cancel.loadAcquire()) {
        summary.elapsedMs = overall.elapsed();
        emit log(QStringLiteral("扫描已取消或无存活主机"));
        emit finished(summary);
        return;
    }

    // 4) 端口扫描任务
    const qint64 totalTasks64 = qint64(scanList.size()) * m_ports.size();
    if (totalTasks64 > 500000) {
        emit log(QStringLiteral("[错误] 总任务量 %1 超过上限 500000，请缩小网段或端口范围")
                     .arg(totalTasks64));
        summary.elapsedMs = overall.elapsed();
        emit finished(summary);
        return;
    }
    const int totalTasks = static_cast<int>(totalTasks64);
    QThreadPool pool;
    pool.setMaxThreadCount(qBound(1, m_opts.concurrency, 1000));
    for (const ScanTarget &t : scanList)
        for (quint16 p : m_ports)
            pool.start(new PortTask(t.address, t.label, p, &m_ctx));

    emit log(QStringLiteral("开始端口扫描，并发 %1 ...").arg(pool.maxThreadCount()));

    QSet<QString> reportedHosts;
    int lastReportedProgress = -1;
    QElapsedTimer uiTick;
    uiTick.start();

    // 5) 边扫描边回收结果（waitForDone 每 50ms 醒一次）
    while (!pool.waitForDone(50)) {
        QVector<PortResult> batch;
        {
            QMutexLocker lock(&m_ctx.resultMutex);
            batch.swap(m_ctx.results);
        }
        for (const PortResult &pr : batch) {
            if (!reportedHosts.contains(pr.host)) {
                reportedHosts.insert(pr.host);
                emit hostUp(pr.host, labelOf.value(pr.host, pr.host));
            }
            emit portFound(pr);
            summary.openPorts++;
            for (const VulnFinding &f : pr.findings) {
                switch (f.severity) {
                case SevCritical: ++summary.critical; break;
                case SevHigh:     ++summary.high; break;
                case SevMedium:   ++summary.medium; break;
                case SevLow:      ++summary.low; break;
                default:          ++summary.info; break;
                }
            }
        }

        if (uiTick.elapsed() >= 200 || m_ctx.cancel.loadAcquire()) {
            const int done = m_ctx.doneCount.loadAcquire();
            if (done != lastReportedProgress) {
                emit progress(qMin(done, totalTasks), totalTasks);
                lastReportedProgress = done;
            }
            uiTick.restart();
        }
        if (m_ctx.cancel.loadAcquire())
            emit log(QStringLiteral("正在停止，等待在途连接超时..."));
    }

    // 收尾批次
    QVector<PortResult> tail;
    {
        QMutexLocker lock(&m_ctx.resultMutex);
        tail.swap(m_ctx.results);
    }
    for (const PortResult &pr : tail) {
        if (!reportedHosts.contains(pr.host)) {
            reportedHosts.insert(pr.host);
            emit hostUp(pr.host, labelOf.value(pr.host, pr.host));
        }
        emit portFound(pr);
        summary.openPorts++;
        for (const VulnFinding &f : pr.findings) {
            switch (f.severity) {
            case SevCritical: ++summary.critical; break;
            case SevHigh:     ++summary.high; break;
            case SevMedium:   ++summary.medium; break;
            case SevLow:      ++summary.low; break;
            default:          ++summary.info; break;
            }
        }
    }

    summary.hostsUp = m_opts.hostDiscovery ? scanList.size() : reportedHosts.size();
    summary.hostsTotal = resolved.size();
    summary.elapsedMs = overall.elapsed();
    emit progress(totalTasks, totalTasks);
    emit log(QStringLiteral("扫描结束：开放端口 %1，严重 %2 / 高危 %3 / 中危 %4 / 低危 %5 / 信息 %6，耗时 %7s")
                 .arg(summary.openPorts)
                 .arg(summary.critical).arg(summary.high).arg(summary.medium)
                 .arg(summary.low).arg(summary.info)
                 .arg(summary.elapsedMs / 1000.0, 0, 'f', 1));
    emit finished(summary);
}
