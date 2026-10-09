#include "targetparser.h"

#include <QHostAddress>
#include <QHostInfo>
#include <QRegularExpression>
#include <QSet>

QVector<ScanTarget> TargetParser::parse(const QString &input, QStringList *err)
{
    QVector<ScanTarget> out;
    QStringList tokens = input.split(QRegularExpression(QStringLiteral("[\\s,;]+")),
                                     QString::SkipEmptyParts);
    QSet<QString> dedup;

    for (const QString &raw : tokens) {
        const QString t = raw.trimmed();
        if (t.isEmpty())
            continue;

        QVector<ScanTarget> items;
        if (t.contains(QLatin1Char('/')))
            items = parseCidr(t, err);
        else if (t.contains(QLatin1Char('-')))
            items = parseRange(t, err);
        else {
            QHostAddress addr(t);
            if (addr.protocol() == QAbstractSocket::IPv4Protocol
                || addr.protocol() == QAbstractSocket::IPv6Protocol)
                items.append({t, t});
            else
                items.append({t, t}); // 域名：扫描前再解析
        }

        for (const ScanTarget &it : items) {
            if (!dedup.contains(it.address)) {
                dedup.insert(it.address);
                out.append(it);
            }
        }
    }
    return out;
}

QVector<ScanTarget> TargetParser::parseCidr(const QString &cidr, QStringList *err)
{
    QVector<ScanTarget> out;
    const int slash = cidr.indexOf(QLatin1Char('/'));
    const QString ipStr = cidr.left(slash);
    bool ok = false;
    int mask = cidr.mid(slash + 1).toInt(&ok);

    QHostAddress base(ipStr);
    if (!ok || mask < 1 || mask > 32 || base.protocol() != QAbstractSocket::IPv4Protocol) {
        if (err) err->append(QStringLiteral("非法 CIDR: %1").arg(cidr));
        return out;
    }

    // toIPv4Address / QHostAddress(quint32) 均为主机字节序
    quint32 ip = base.toIPv4Address();
    quint32 maskBits = (mask == 0) ? 0 : (0xFFFFFFFFu << (32 - mask));
    quint32 network = ip & maskBits;
    quint32 count = 1u << (32 - mask);

    // 普通网段跳过网络地址与广播地址；/31、/32 全给
    quint32 start = (count > 2) ? network + 1 : network;
    quint32 end = (count > 2) ? network + count - 2 : network + count - 1;

    for (quint32 v = start; v <= end; ++v)
        out.append({QHostAddress(v).toString(), QHostAddress(v).toString()});
    return out;
}

QVector<ScanTarget> TargetParser::parseRange(const QString &range, QStringList *err)
{
    QVector<ScanTarget> out;
    const QStringList parts = range.split(QLatin1Char('-'));
    if (parts.size() != 2) {
        if (err) err->append(QStringLiteral("非法范围: %1").arg(range));
        return out;
    }

    QString left = parts[0].trimmed();
    QString right = parts[1].trimmed();
    QHostAddress a(left);
    if (a.protocol() != QAbstractSocket::IPv4Protocol) {
        if (err) err->append(QStringLiteral("非法起始 IP: %1").arg(range));
        return out;
    }

    // 192.168.1.1-50  => 末段范围
    if (!right.contains(QLatin1Char('.'))) {
        bool ok = false;
        int last = right.toInt(&ok);
        if (!ok || last < 0 || last > 255) {
            if (err) err->append(QStringLiteral("非法范围尾段: %1").arg(range));
            return out;
        }
        quint32 base = a.toIPv4Address(); // 主机字节序
        int first = static_cast<int>(base & 0xFF);
        quint32 prefix = base & 0xFFFFFF00u;
        if (last < first) std::swap(first, last);
        for (int i = first; i <= last; ++i)
            out.append({QHostAddress(prefix | static_cast<quint32>(i)).toString(), QString()});
        for (ScanTarget &t : out) t.label = t.address;
        return out;
    }

    // 完整 IP 区间
    QHostAddress b(right);
    if (b.protocol() != QAbstractSocket::IPv4Protocol) {
        if (err) err->append(QStringLiteral("非法结束 IP: %1").arg(range));
        return out;
    }
    quint32 va = a.toIPv4Address();
    quint32 vb = b.toIPv4Address();
    if (vb < va) std::swap(va, vb);
    // 防止误输入 /8 级别的巨型范围
    if (vb - va > 65535u) {
        if (err) err->append(QStringLiteral("范围过大(>65536): %1").arg(range));
        return out;
    }
    for (quint32 v = va; v <= vb; ++v)
        out.append({QHostAddress(v).toString(), QHostAddress(v).toString()});
    return out;
}

QString TargetParser::resolveHostname(const QString &name, int /*timeoutMs*/)
{
    QHostAddress addr(name);
    if (addr.protocol() == QAbstractSocket::IPv4Protocol
        || addr.protocol() == QAbstractSocket::IPv6Protocol)
        return name;

    // 本函数只会在扫描工作线程中调用，阻塞式解析不影响 UI
    QHostInfo info = QHostInfo::fromName(name);
    const auto addrs = info.addresses();
    for (const QHostAddress &ha : addrs) {
        if (ha.protocol() == QAbstractSocket::IPv4Protocol)
            return ha.toString();
    }
    return QString();
}
