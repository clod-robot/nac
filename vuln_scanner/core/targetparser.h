#ifndef TARGETPARSER_H
#define TARGETPARSER_H

#include <QString>
#include <QStringList>
#include <QVector>

struct ScanTarget {
    QString address;  // 实际扫描的 IP
    QString label;    // 原始输入（域名等）
};

class TargetParser
{
public:
    // 解析多行输入（支持 单IP / CIDR / a.b.c.d-x / a.b.c.x-y / 域名）
    // err 输出解析错误信息
    static QVector<ScanTarget> parse(const QString &input, QStringList *err = nullptr);

    // 在工作线程中同步解析域名，返回解析出的 IPv4；失败返回空串
    static QString resolveHostname(const QString &name, int timeoutMs = 3000);

private:
    static QVector<ScanTarget> parseCidr(const QString &cidr, QStringList *err);
    static QVector<ScanTarget> parseRange(const QString &range, QStringList *err);
};

#endif // TARGETPARSER_H
