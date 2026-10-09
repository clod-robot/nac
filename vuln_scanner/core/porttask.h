#ifndef PORTTASK_H
#define PORTTASK_H

#include <QRunnable>
#include "scanengine.h"

// 单个 host:port 的 TCP 全连接扫描 + 服务识别
class PortTask : public QRunnable
{
public:
    PortTask(const QString &host, const QString &label, quint16 port,
             ScanContext *ctx);

    void run() override;

private:
    QString m_host;
    QString m_label;
    quint16 m_port;
    ScanContext *m_ctx;
};

// 主机存活探测：对少量常用端口做快速 TCP 连接
class HostPingTask : public QRunnable
{
public:
    HostPingTask(const QString &host, const QString &label, ScanContext *ctx);
    void run() override;

private:
    QString m_host;
    QString m_label;
    ScanContext *m_ctx;
};

#endif // PORTTASK_H
