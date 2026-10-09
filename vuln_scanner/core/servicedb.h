#ifndef SERVICEDB_H
#define SERVICEDB_H

#include <QHash>
#include <QSet>
#include <QString>
#include <QVector>

// 知名端口表与扫描预设
class ServiceDB
{
public:
    static const QHash<quint16, QString> &knownPorts();
    static QString serviceHint(quint16 port);

    // 主动发送 HTTP 探测的端口
    static const QSet<quint16> &httpPorts();
    // 优先 TLS 的端口
    static const QSet<quint16> &tlsPorts();
    // 服务端会主动发送 Banner 的协议（被动等待即可）
    static const QSet<quint16> &passivePorts();

    // 预设端口列表
    static QVector<quint16> presetTopPorts();      // 常用 30
    static QVector<quint16> presetWebPorts();      // Web/中间件
    static QVector<quint16> presetDevicePorts();   // 网络设备/安防
    static QVector<quint16> presetStandardPorts(); // 常用约 100
    static QVector<quint16> allPorts();            // 1-65535

    // 解析 "22,80,1-1000,3306" 形式的自定义端口串
    static QVector<quint16> parsePortSpec(const QString &spec, QString *err = nullptr);
};

#endif // SERVICEDB_H
