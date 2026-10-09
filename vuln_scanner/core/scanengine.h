#ifndef SCANENGINE_H
#define SCANENGINE_H

#include <QObject>
#include <QThread>
#include <QVector>
#include <QMutex>
#include <QSet>
#include <QAtomicInt>
#include "targetparser.h"
#include "../model/scantypes.h"

class VulnDb;

struct ScanOptions {
    int connectTimeoutMs = 2000;   // TCP 连接超时
    int readTimeoutMs = 3000;      // 协议读取超时
    int concurrency = 100;         // 并发连接数
    bool hostDiscovery = true;     // 是否先做主机存活探测
};

// 多线程任务共享的上下文（队列 + 取消标志）
struct ScanContext {
    const VulnDb *db = nullptr;
    ScanOptions opts;
    QAtomicInt cancel{0};
    QAtomicInt doneCount{0};

    QMutex resultMutex;
    QVector<PortResult> results;   // 仅入队开放端口

    QMutex aliveMutex;
    QSet<QString> aliveHosts;
};

// 扫描引擎：自身在 worker QThread 中运行，端口任务跑在 QThreadPool
class ScanEngine : public QObject
{
    Q_OBJECT
public:
    explicit ScanEngine(const VulnDb *db, QObject *parent = nullptr);
    ~ScanEngine();

    void configure(const QString &targetInput, const QVector<quint16> &ports,
                   const ScanOptions &opts);

public slots:
    void start();
    void cancel();

signals:
    void log(const QString &msg);
    void hostsEnumerated(int total);
    void hostUp(const QString &address, const QString &label);
    void portFound(const PortResult &result);
    void progress(int done, int total);
    void finished(const ScanSummary &summary);

private:
    void discoverHosts(const QVector<ScanTarget> &targets);
    ScanContext m_ctx;
    QString m_targetInput;
    QVector<quint16> m_ports;
    ScanOptions m_opts;
    QThread *m_thread = nullptr;
};

#endif // SCANENGINE_H
