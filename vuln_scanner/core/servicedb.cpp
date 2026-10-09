#include "servicedb.h"

#include <QStringList>

static QHash<quint16, QString> buildKnownPorts()
{
    QHash<quint16, QString> m;
    m[21]    = QStringLiteral("ftp");
    m[22]    = QStringLiteral("ssh");
    m[23]    = QStringLiteral("telnet");
    m[25]    = QStringLiteral("smtp");
    m[53]    = QStringLiteral("dns");
    m[69]    = QStringLiteral("tftp");
    m[80]    = QStringLiteral("http");
    m[110]   = QStringLiteral("pop3");
    m[111]   = QStringLiteral("rpcbind");
    m[119]   = QStringLiteral("nntp");
    m[135]   = QStringLiteral("msrpc");
    m[139]   = QStringLiteral("netbios-ssn");
    m[143]   = QStringLiteral("imap");
    m[161]   = QStringLiteral("snmp");
    m[389]   = QStringLiteral("ldap");
    m[443]   = QStringLiteral("https");
    m[445]   = QStringLiteral("smb");
    m[465]   = QStringLiteral("smtps");
    m[554]   = QStringLiteral("rtsp");
    m[1935]  = QStringLiteral("rtmp");
    m[512]   = QStringLiteral("rexec");
    m[513]   = QStringLiteral("rlogin");
    m[514]   = QStringLiteral("rsh");
    m[587]   = QStringLiteral("submission");
    m[623]   = QStringLiteral("asf-rmcp");
    m[636]   = QStringLiteral("ldaps");
    m[873]   = QStringLiteral("rsync");
    m[990]   = QStringLiteral("ftps");
    m[993]   = QStringLiteral("imaps");
    m[995]   = QStringLiteral("pop3s");
    m[1099]  = QStringLiteral("rmi-registry");
    m[1433]  = QStringLiteral("mssql");
    m[1521]  = QStringLiteral("oracle");
    m[1883]  = QStringLiteral("mqtt");
    m[2049]  = QStringLiteral("nfs");
    m[2181]  = QStringLiteral("zookeeper");
    m[2323]  = QStringLiteral("device-telnet");
    m[2375]  = QStringLiteral("docker");
    m[2376]  = QStringLiteral("docker-tls");
    m[2379]  = QStringLiteral("etcd");
    m[2424]  = QStringLiteral("orientdb");
    m[3000]  = QStringLiteral("http-alt");
    m[3001]  = QStringLiteral("http-alt");
    m[3128]  = QStringLiteral("squid-proxy");
    m[3306]  = QStringLiteral("mysql");
    m[3389]  = QStringLiteral("rdp");
    m[34567] = QStringLiteral("dvr-native");
    m[34568] = QStringLiteral("dvr-native");
    m[37777] = QStringLiteral("dvr-dahua");
    m[37810] = QStringLiteral("dvr-alarm");
    m[4040]  = QStringLiteral("http-alt");
    m[4369]  = QStringLiteral("epmd");
    m[4443]  = QStringLiteral("https-alt");
    m[4848]  = QStringLiteral("glassfish-admin");
    m[5000]  = QStringLiteral("http-alt");
    m[5432]  = QStringLiteral("postgresql");
    m[5601]  = QStringLiteral("kibana");
    m[5672]  = QStringLiteral("amqp");
    m[5900]  = QStringLiteral("vnc");
    m[5984]  = QStringLiteral("couchdb");
    m[5985]  = QStringLiteral("winrm");
    m[5986]  = QStringLiteral("winrm-ssl");
    m[6000]  = QStringLiteral("x11");
    m[6379]  = QStringLiteral("redis");
    m[6443]  = QStringLiteral("kubernetes-api");
    m[7001]  = QStringLiteral("weblogic");
    m[7002]  = QStringLiteral("weblogic-ssl");
    m[7077]  = QStringLiteral("spark");
    m[8000]  = QStringLiteral("http-alt");
    m[8008]  = QStringLiteral("http-alt");
    m[8009]  = QStringLiteral("ajp");
    m[8025]  = QStringLiteral("http-alt");
    m[8042]  = QStringLiteral("hadoop-nm");
    m[8060]  = QStringLiteral("http-alt");
    m[8069]  = QStringLiteral("http-alt");
    m[8080]  = QStringLiteral("http-proxy");
    m[8081]  = QStringLiteral("http-alt");
    m[8086]  = QStringLiteral("influxdb");
    m[8088]  = QStringLiteral("http-alt");
    m[8089]  = QStringLiteral("http-alt");
    m[8090]  = QStringLiteral("http-alt");
    m[8091]  = QStringLiteral("couchbase");
    m[8161]  = QStringLiteral("activemq-admin");
    m[8443]  = QStringLiteral("https-alt");
    m[8444]  = QStringLiteral("https-alt");
    m[8500]  = QStringLiteral("http-alt");
    m[8530]  = QStringLiteral("http-alt");
    m[8686]  = QStringLiteral("http-alt");
    m[8800]  = QStringLiteral("http-alt");
    m[8848]  = QStringLiteral("nacos");
    m[8888]  = QStringLiteral("http-alt");
    m[8899]  = QStringLiteral("onvif-alt");
    m[8983]  = QStringLiteral("solr");
    m[9000]  = QStringLiteral("http-alt");
    m[9001]  = QStringLiteral("http-alt");
    m[9006]  = QStringLiteral("http-alt");
    m[9042]  = QStringLiteral("cassandra");
    m[9090]  = QStringLiteral("http-alt");
    m[9092]  = QStringLiteral("kafka");
    m[9100]  = QStringLiteral("http-alt");
    m[9200]  = QStringLiteral("elasticsearch");
    m[9300]  = QStringLiteral("es-transport");
    m[9418]  = QStringLiteral("git");
    m[9443]  = QStringLiteral("https-alt");
    m[9999]  = QStringLiteral("http-alt");
    m[10000] = QStringLiteral("http-alt");
    m[10250] = QStringLiteral("kubelet");
    m[11211] = QStringLiteral("memcached");
    m[15672] = QStringLiteral("rabbitmq-admin");
    m[15692] = QStringLiteral("mqtt-ssl");
    m[27017] = QStringLiteral("mongodb");
    m[27018] = QStringLiteral("mongodb");
    m[50070] = QStringLiteral("hadoop-namenode");
    m[50090] = QStringLiteral("http-alt");
    m[61616] = QStringLiteral("activemq");
    return m;
}

const QHash<quint16, QString> &ServiceDB::knownPorts()
{
    static const QHash<quint16, QString> m = buildKnownPorts();
    return m;
}

QString ServiceDB::serviceHint(quint16 port)
{
    return knownPorts().value(port);
}

const QSet<quint16> &ServiceDB::httpPorts()
{
    static const QSet<quint16> s = {
        80, 443, 2375, 3000, 3001, 4040, 4443, 4848, 5000, 5601, 5984, 7001,
        8000, 8008, 8025, 8060, 8069, 8080, 8081, 8086, 8088, 8089, 8090, 8161,
        8443, 8444, 8500, 8530, 8686, 8800, 8848, 8888, 8983, 9000, 9001, 9006,
        9090, 9100, 9200, 9443, 9999, 10000, 10250, 15672, 50070, 50090
    };
    return s;
}

const QSet<quint16> &ServiceDB::tlsPorts()
{
    static const QSet<quint16> s = {443, 465, 636, 990, 993, 995, 2376, 5986,
                                    8443, 8444, 9443, 15692, 6443};
    return s;
}

const QSet<quint16> &ServiceDB::passivePorts()
{
    // 这些端口的服务端在 TCP 建连后会先发送协议 Banner
    static const QSet<quint16> s = {21, 22, 23, 25, 110, 119, 143, 465,
                                    587, 990, 993, 995, 3306, 5900};
    return s;
}

QVector<quint16> ServiceDB::presetTopPorts()
{
    static const QVector<quint16> p = {
        21, 22, 23, 25, 53, 80, 110, 111, 135, 139, 143, 443, 445, 465, 587,
        993, 995, 1433, 1521, 2049, 2375, 3306, 3389, 5432, 5900, 5985, 6379,
        6443, 7001, 8009, 8080, 8443, 8848, 9200, 9300, 11211, 27017, 50070
    };
    return p;
}

QVector<quint16> ServiceDB::presetWebPorts()
{
    static const QVector<quint16> p = {
        80, 443, 2375, 3000, 4848, 5000, 5601, 7001, 8000, 8080, 8081, 8088,
        8090, 8161, 8443, 8500, 8848, 8888, 8983, 9000, 9090, 9200, 9443,
        10250, 15672, 50070
    };
    return p;
}

QVector<quint16> ServiceDB::presetDevicePorts()
{
    static const QVector<quint16> p = {
        22, 23, 80, 443, 161, 554, 1935, 2323, 37777, 37810, 34567,
        4786, 5900, 8000, 8080, 8443, 8899
    };
    return p;
}

QVector<quint16> ServiceDB::presetStandardPorts()
{
    static const QVector<quint16> p = [] {
        QSet<quint16> s;
        for (quint16 p : presetTopPorts()) s.insert(p);
        for (quint16 p : presetWebPorts()) s.insert(p);
        for (quint16 p : presetDevicePorts()) s.insert(p);
        s << 69 << 389 << 512 << 513 << 514 << 623 << 636 << 873 << 1099
          << 1883 << 2181 << 2376 << 2379 << 2424 << 3128 << 4040 << 4369
          << 5672 << 5984 << 5986 << 6000 << 7077 << 8008 << 8042 << 8069
          << 8086 << 8091 << 8800 << 9042 << 9092 << 9418 << 9999 << 10000
          << 61616;
        QVector<quint16> v = s.values().toVector();
        std::sort(v.begin(), v.end());
        return v;
    }();
    return p;
}

QVector<quint16> ServiceDB::allPorts()
{
    QVector<quint16> v;
    v.reserve(65535);
    for (int i = 1; i <= 65535; ++i)
        v.append(static_cast<quint16>(i));
    return v;
}

QVector<quint16> ServiceDB::parsePortSpec(const QString &spec, QString *err)
{
    QVector<quint16> out;
    QSet<int> dedup;
    const QStringList parts = spec.split(QLatin1Char(','), QString::SkipEmptyParts);
    for (const QString &part : parts) {
        const QString t = part.trimmed();
        if (t.contains(QLatin1Char('-'))) {
            const QStringList seg = t.split(QLatin1Char('-'));
            if (seg.size() != 2) { if (err) *err = QStringLiteral("非法端口段: ") + t; return {}; }
            bool a = false, b = false;
            int lo = seg[0].trimmed().toInt(&a);
            int hi = seg[1].trimmed().toInt(&b);
            if (!a || !b || lo < 1 || hi > 65535 || lo > hi) {
                if (err) *err = QStringLiteral("非法端口段: ") + t;
                return {};
            }
            if (hi - lo > 5000) {
                if (err) *err = QStringLiteral("单段最多 5000 个端口: ") + t;
                return {};
            }
            for (int i = lo; i <= hi; ++i)
                if (!dedup.contains(i)) {
                    dedup.insert(i);
                    out.append(static_cast<quint16>(i));
                }
        } else {
            bool ok = false;
            int p = t.toInt(&ok);
            if (!ok || p < 1 || p > 65535) {
                if (err) *err = QStringLiteral("非法端口: ") + t;
                return {};
            }
            if (!dedup.contains(p)) {
                dedup.insert(p);
                out.append(static_cast<quint16>(p));
            }
        }
    }
    std::sort(out.begin(), out.end());
    return out;
}
