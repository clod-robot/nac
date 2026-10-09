#include "porttask.h"
#include "servicedb.h"
#include "../vuln/vulndb.h"

#include <QTcpSocket>
#include <QSslSocket>
#include <QElapsedTimer>
#include <QSet>
#include <QRegularExpression>

// ---------------------------------------------------------------------------
// 工具函数
// ---------------------------------------------------------------------------

static const int MAX_BANNER = 1024;
static const int MAX_BODY = 64 * 1024;

static QString sanitize(const QByteArray &b)
{
    QString s = QString::fromLatin1(b);
    QString out;
    int printable = 0;
    for (const QChar c : s) {
        if (c == '\r' || c == '\n' || c == '\t') {
            out.append(c);
            ++printable;
        } else if (c.category() != QChar::Other_Control && c.unicode() >= 0x20) {
            out.append(c);
            ++printable;
        } else {
            out.append(QLatin1Char('.'));
        }
    }
    if (s.isEmpty() || printable * 10 < s.size() * 7)
        return QString(); // 可打印比例过低，视为二进制
    return out.simplified().left(MAX_BANNER);
}

// 在剩余时间内尽量读取（服务端不主动关闭时由 deadline 收口）
static QByteArray readAvailable(QTcpSocket *s, int totalMs)
{
    QByteArray out;
    QElapsedTimer t;
    t.start();
    while (t.elapsed() < totalMs) {
        if (s->state() != QAbstractSocket::ConnectedState)
            break;
        if (s->waitForReadyRead(150)) {
            out += s->readAll();
            if (out.size() > MAX_BODY) { out.truncate(MAX_BODY); break; }
        }
    }
    if (s->bytesAvailable() > 0)
        out += s->read(65536);
    return out;
}

static bool canceled(ScanContext *c) { return c->cancel.loadAcquire() != 0; }

// 指纹识别 + 规则匹配 + 入队（仅开放端口）
static void submitResult(PortResult &r, ScanContext *ctx)
{
    ctx->db->identify(r);
    r.findings = ctx->db->match(r);
    QMutexLocker lock(&ctx->resultMutex);
    ctx->results.append(r);
}

// ---------------------------------------------------------------------------
// HTTP / RTSP 解析与探测
// ---------------------------------------------------------------------------

struct ParsedHttp {
    bool ok = false;
    bool rtsp = false;
    int status = 0;
    QMap<QString, QString> headers; // key 已小写
    QByteArray body;
};

static ParsedHttp parseHttpLike(const QByteArray &raw)
{
    ParsedHttp p;
    const int headEnd = raw.indexOf("\r\n\r\n");
    const QByteArray head = headEnd >= 0 ? raw.left(headEnd) : raw;
    const QList<QByteArray> lines = head.split('\n');
    if (lines.isEmpty())
        return p;
    const QByteArray first = lines.first().trimmed();
    if (!first.startsWith("HTTP/") && !first.startsWith("RTSP/"))
        return p;
    p.rtsp = first.startsWith("RTSP/");
    const QList<QByteArray> fs = first.split(' ');
    if (fs.size() >= 2) p.status = fs[1].toInt();

    for (int i = 1; i < lines.size(); ++i) {
        const QByteArray line = lines[i].trimmed();
        const int colon = line.indexOf(':');
        if (colon > 0)
            p.headers.insert(QString::fromLatin1(line.left(colon)).toLower(),
                             QString::fromLatin1(line.mid(colon + 1)).trimmed());
    }
    if (headEnd >= 0)
        p.body = raw.mid(headEnd + 4);
    p.ok = p.status > 0;
    return p;
}

static QByteArray buildHttpRequest(const QString &host, quint16 port,
                                   const QString &path, bool tls)
{
    QString hostHdr = host;
    if (!(port == 80 && !tls) && !(port == 443 && tls))
        hostHdr += QStringLiteral(":") + QString::number(port);
    QByteArray req;
    req.append("GET ").append(path.toUtf8()).append(" HTTP/1.0\r\n");
    req.append("Host: ").append(hostHdr.toUtf8()).append("\r\n");
    req.append("User-Agent: VulnScanner/1.0 (authorized assessment)\r\n");
    req.append("Accept: */*\r\n");
    req.append("Connection: close\r\n\r\n");
    return req;
}

// 在已连接的 socket 上发送一次请求并读取完整响应（按 Content-Length 提前收口）
static QByteArray httpTransaction(QTcpSocket *s, const QByteArray &req, int deadlineMs)
{
    s->write(req);
    s->flush();
    QByteArray out;
    QElapsedTimer t;
    t.start();
    qint64 contentLen = -1;
    bool headParsed = false;

    while (t.elapsed() < deadlineMs) {
        if (s->state() != QAbstractSocket::ConnectedState) {
            out += s->readAll();
            break;
        }
        if (!s->waitForReadyRead(200))
            continue;
        out += s->readAll();

        if (!headParsed && out.indexOf("\r\n\r\n") >= 0) {
            headParsed = true;
            ParsedHttp hp = parseHttpLike(out);
            bool clOk = false;
            const qint64 cl = hp.headers.value(QStringLiteral("content-length"))
                                  .toLongLong(&clOk);
            if (clOk) contentLen = cl;
        }
        if (headParsed && contentLen >= 0) {
            const int bodyStart = out.indexOf("\r\n\r\n") + 4;
            if (out.size() - bodyStart >= contentLen) break;
        }
        if (out.size() > MAX_BODY) { out.truncate(MAX_BODY); break; }
    }
    return out;
}

static QString httpPathFor(quint16 port)
{
    switch (port) {
    case 2375: return QStringLiteral("/version");
    case 8848: return QStringLiteral("/nacos/v1/auth/users?pageNo=1&pageSize=10");
    case 10250: return QStringLiteral("/pods");
    case 8983: return QStringLiteral("/solr/admin/");
    case 8500: return QStringLiteral("/v1/agent/self");
    case 2379: return QStringLiteral("/version");
    case 6443: return QStringLiteral("/version");
    default:   return QStringLiteral("/");
    }
}

static void applyHttpResult(PortResult &r, const ParsedHttp &p, bool tls)
{
    r.httpStatus = p.status;
    r.httpHeaders = p.headers;
    r.httpServer = p.headers.value(QStringLiteral("server"));
    r.httpPoweredBy = p.headers.value(QStringLiteral("x-powered-by"));
    r.tls = tls;
    if (r.service.isEmpty() || r.service == r.serviceHint)
        r.service = tls ? QStringLiteral("https") : QStringLiteral("http");

    QString body = QString::fromUtf8(p.body.left(4096));
    r.httpBody = body;
    static const QRegularExpression titleRe(QStringLiteral("<title[^>]*>([^<]+)</title>"),
                                            QRegularExpression::CaseInsensitiveOption);
    QRegularExpressionMatch m = titleRe.match(body);
    if (m.hasMatch())
        r.httpTitle = m.captured(1).simplified();

    const QString b = body;
    switch (r.port) {
    case 2375:
        r.unauth = (p.status == 200);
        break;
    case 9200:
        r.unauth = (p.status == 200 && b.contains(QStringLiteral("cluster_name")));
        break;
    case 8848:
        r.unauth = (p.status == 200
                    && (b.contains(QStringLiteral("totalCount"))
                        || b.contains(QStringLiteral("pageItems"))
                        || b.contains(QStringLiteral("\"username\""))));
        break;
    case 10250:
        r.unauth = (p.status == 200 && b.contains(QStringLiteral("\"items\"")));
        break;
    case 8500:
        r.unauth = (p.status == 200 && b.contains(QStringLiteral("Config")));
        break;
    case 5984:
        r.unauth = (p.status == 200 && b.contains(QStringLiteral("couchdb")));
        break;
    case 50070: case 8042:
        r.unauth = (p.status == 200 && b.contains(QStringLiteral("Hadoop"), Qt::CaseInsensitive));
        break;
    case 5601:
        r.unauth = (p.status == 200
                    && (b.contains(QStringLiteral("kbn-name"), Qt::CaseInsensitive)
                        || b.contains(QStringLiteral("kibana"), Qt::CaseInsensitive)));
        break;
    case 8983:
        r.unauth = (p.status == 200
                    && (b.contains(QStringLiteral("Solr"), Qt::CaseInsensitive)
                        || p.headers.contains(QStringLiteral("x-solr"))));
        break;
    default: break;
    }
}

// 完整 HTTP 探测（含一次同机重定向跟随）；socket 由调用方负责
static bool probeHttp(QTcpSocket *s, const QString &host, quint16 port,
                      bool tls, int readTimeout, PortResult &r)
{
    QString path = httpPathFor(port);
    QByteArray raw = httpTransaction(s, buildHttpRequest(host, port, path, tls),
                                     readTimeout);
    ParsedHttp p = parseHttpLike(raw);
    if (!p.ok)
        return false;

    // 301/302/307 跟随一次（仅允许同源）
    if ((p.status == 301 || p.status == 302 || p.status == 307)
        && p.headers.contains(QStringLiteral("location"))) {
        const QString loc = p.headers.value(QStringLiteral("location"));
        QString nextPath;
        bool nextTls = tls;
        if (loc.startsWith(QStringLiteral("http://"), Qt::CaseInsensitive)
            || loc.startsWith(QStringLiteral("https://"), Qt::CaseInsensitive)) {
            static const QRegularExpression re(
                QStringLiteral("^https?://[^/]+(/.*)?$"),
                QRegularExpression::CaseInsensitiveOption);
            QRegularExpressionMatch mm = re.match(loc);
            if (!mm.hasMatch()) { applyHttpResult(r, p, tls); return true; }
            nextTls = loc.startsWith(QStringLiteral("https"), Qt::CaseInsensitive);
            nextPath = mm.captured(1);
            // 仅跟随同主机同端口
            static const QRegularExpression hostRe(
                QStringLiteral("^https?://([^/]+)"), QRegularExpression::CaseInsensitiveOption);
            const QString auth = hostRe.match(loc).captured(1);
            if (!auth.startsWith(host, Qt::CaseInsensitive)) {
                applyHttpResult(r, p, tls); return true;
            }
        } else {
            nextPath = loc;
        }
        if (nextPath.isEmpty()) nextPath = QStringLiteral("/");

        QTcpSocket *s2 = nullptr;
        QSslSocket *ssl2 = nullptr;
        if (nextTls) {
            ssl2 = new QSslSocket();
            s2 = ssl2;
        } else {
            s2 = new QTcpSocket();
        }
        bool connected = false;
        if (ssl2) {
            ssl2->ignoreSslErrors();
            ssl2->connectToHostEncrypted(host, port);
            connected = ssl2->waitForEncrypted(2000);
        } else {
            s2->connectToHost(host, port);
            connected = s2->waitForConnected(2000);
        }
        if (connected) {
            raw = httpTransaction(s2, buildHttpRequest(host, port, nextPath, nextTls),
                                  readTimeout);
            ParsedHttp p2 = parseHttpLike(raw);
            if (p2.ok) p = p2;
        }
        delete s2;
    }

    applyHttpResult(r, p, tls);
    return true;
}

// ---------------------------------------------------------------------------
// 专用协议探测
// ---------------------------------------------------------------------------

static void probeRedis(QTcpSocket *s, int readTimeout, PortResult &r)
{
    s->write("PING\r\n");
    s->flush();
    QByteArray reply = readAvailable(s, qMin(1200, readTimeout));
    QString text = QString::fromLatin1(reply).trimmed();
    if (text.startsWith(QStringLiteral("+PONG"))) {
        r.unauth = true;
        s->write("INFO\r\n");
        s->flush();
        reply = readAvailable(s, qMin(1500, readTimeout));
        const QString info = QString::fromLatin1(reply);
        QString ver, os;
        for (const QString &line : info.split(QLatin1Char('\n'))) {
            if (line.startsWith(QStringLiteral("redis_version:")))
                ver = line.section(QLatin1Char(':'), 1).trimmed();
            else if (line.startsWith(QStringLiteral("os:")))
                os = line.section(QLatin1Char(':'), 1).trimmed();
        }
        r.service = QStringLiteral("redis");
        r.banner = QStringLiteral("Redis %1 (unauthenticated; %2)")
                       .arg(ver.isEmpty() ? QStringLiteral("?") : ver,
                            os.isEmpty() ? QStringLiteral("os unknown") : os);
    } else if (text.startsWith(QStringLiteral("-NOAUTH"))) {
        r.service = QStringLiteral("redis");
        r.banner = QStringLiteral("Redis (authentication required)");
    } else {
        r.banner = sanitize(reply);
    }
}

static void probeMemcached(QTcpSocket *s, int readTimeout, PortResult &r)
{
    s->write("version\r\n");
    s->flush();
    const QByteArray reply = readAvailable(s, qMin(1200, readTimeout));
    const QString text = QString::fromLatin1(reply);
    if (text.contains(QStringLiteral("VERSION"), Qt::CaseInsensitive)) {
        r.unauth = true;
        r.service = QStringLiteral("memcached");
        r.banner = text.simplified().left(MAX_BANNER);
    }
}

static void probeZookeeper(QTcpSocket *s, int readTimeout, PortResult &r)
{
    s->write("envi\r\n");
    s->flush();
    const QByteArray reply = readAvailable(s, qMin(1500, readTimeout));
    const QString text = QString::fromLatin1(reply);
    if (text.contains(QStringLiteral("zookeeper.version"), Qt::CaseInsensitive)) {
        r.unauth = true;
        r.service = QStringLiteral("zookeeper");
        r.banner = text.simplified().left(MAX_BANNER);
    }
}

static void probeRsync(QTcpSocket *s, int readTimeout, PortResult &r)
{
    QByteArray banner = readAvailable(s, qMin(600, readTimeout));
    if (banner.isEmpty()) return;
    // 协议握手后发空模块名获取模块列表
    s->write("@RSYNCD: 31.0\n\n");
    s->flush();
    const QByteArray list = readAvailable(s, qMin(1200, readTimeout));
    const QString text = (QString::fromLatin1(banner) + QString::fromLatin1(list))
                             .simplified();
    r.service = QStringLiteral("rsync");
    r.banner = text.left(MAX_BANNER);
    const QStringList lines = QString::fromLatin1(list).split(QLatin1Char('\n'));
    int modules = 0;
    for (const QString &ln : lines) {
        const QString t = ln.trimmed();
        if (!t.isEmpty() && !t.startsWith(QStringLiteral("@RSYNCD")))
            ++modules;
    }
    if (modules > 0) r.unauth = true;
}

static void probeRtsp(QTcpSocket *s, int readTimeout, PortResult &r)
{
    QByteArray req;
    req.append("OPTIONS rtsp://").append(r.host.toUtf8())
       .append(":554/ RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: VulnScanner\r\n\r\n");
    s->write(req);
    s->flush();
    const QByteArray raw = readAvailable(s, qMin(1500, readTimeout));
    ParsedHttp p = parseHttpLike(raw);
    if (p.ok) {
        r.service = QStringLiteral("rtsp");
        r.httpStatus = p.status;
        r.httpHeaders = p.headers;
        r.httpServer = p.headers.value(QStringLiteral("server"));
        r.banner = sanitize(raw);
    }
}

static void parseMysqlHandshake(const QByteArray &raw, PortResult &r)
{
    // 结构: 3字节长度+1字节序号, payload 第1字节协议版本(10), 后接 null 结尾版本串
    if (raw.size() < 6) return;
    if (static_cast<quint8>(raw[4]) != 10) return;
    int end = 5;
    while (end < raw.size() && raw[end] != 0) ++end;
    const QByteArray ver = raw.mid(5, end - 5);
    bool printable = true;
    for (char c : ver)
        if (static_cast<quint8>(c) < 0x20 || static_cast<quint8>(c) > 0x7e) { printable = false; break; }
    if (!printable || ver.isEmpty()) return;
    const QString v = QString::fromLatin1(ver);
    r.service = QStringLiteral("mysql");
    if (v.contains(QStringLiteral("mariadb"), Qt::CaseInsensitive)) {
        r.product = QStringLiteral("MariaDB");
        static const QRegularExpression re(QStringLiteral("(\\d+\\.\\d+\\.\\d+)-MariaDB"),
                                           QRegularExpression::CaseInsensitiveOption);
        QRegularExpressionMatch m = re.match(v);
        r.version = m.hasMatch() ? m.captured(1) : v;
    } else {
        r.product = QStringLiteral("MySQL");
        static const QRegularExpression re(QStringLiteral("^(\\d+\\.\\d+\\.\\d+)"));
        QRegularExpressionMatch m = re.match(v);
        r.version = m.hasMatch() ? m.captured(1) : v.section(QLatin1Char('-'), 0, 0);
    }
    r.banner = v.left(MAX_BANNER);
}

// 不主动发送探测、不做 HTTP 兜底的二进制/私有协议端口
static bool isBinaryOnlyPort(quint16 port)
{
    static const QSet<quint16> s = {
        111, 135, 139, 445, 1433, 1521, 1883, 2049, 27017, 27018,
        3389, 5672, 61616, 623, 9042, 9092
    };
    return s.contains(port);
}

// ---------------------------------------------------------------------------
// PortTask
// ---------------------------------------------------------------------------

PortTask::PortTask(const QString &host, const QString &label, quint16 port,
                   ScanContext *ctx)
    : m_host(host), m_label(label), m_port(port), m_ctx(ctx)
{
    setAutoDelete(true);
}

void PortTask::run()
{
    m_ctx->doneCount.ref();
    if (canceled(m_ctx)) return;

    const ScanOptions &opts = m_ctx->opts;
    const bool useTls = ServiceDB::tlsPorts().contains(m_port);

    QTcpSocket *sock = nullptr;
    QSslSocket *ssl = nullptr;
    if (useTls) {
        ssl = new QSslSocket();
        sock = ssl;
    } else {
        sock = new QTcpSocket();
    }

    PortResult r;
    r.host = m_host;
    r.port = m_port;
    r.serviceHint = ServiceDB::serviceHint(m_port);
    r.service = r.serviceHint;

    QElapsedTimer timer;
    timer.start();
    bool connected = false;

    if (ssl) {
        if (!QSslSocket::supportsSsl()) {
            r.open = true;
            r.banner = QStringLiteral("[SSL/TLS 支持不可用，跳过加密探测]");
            delete sock;
            submitResult(r, m_ctx);
            return;
        }
        // 先建立 TCP，再单独握手：区分"端口未开放"与"握手失败"
        ssl->connectToHost(m_host, m_port);
        if (!ssl->waitForConnected(opts.connectTimeoutMs)) {
            delete sock;
            return;
        }
        connected = true;
        ssl->ignoreSslErrors();
        ssl->startClientEncryption();
        if (!ssl->waitForEncrypted(opts.connectTimeoutMs)) {
            r.open = true;
            r.latencyMs = static_cast<int>(timer.elapsed());
            r.banner = QStringLiteral("[TLS 握手失败: %1]").arg(ssl->errorString());
            delete sock;
            submitResult(r, m_ctx);
            return;
        }
    } else {
        sock->connectToHost(m_host, m_port);
        connected = sock->waitForConnected(opts.connectTimeoutMs);
    }

    if (!connected) {
        delete sock;
        return;
    }
    r.open = true;
    r.latencyMs = static_cast<int>(timer.elapsed());

    // ---- 按端口选择探测方式 ----
    if (useTls && ssl) {
        probeHttp(sock, m_host, m_port, true, opts.readTimeoutMs, r);
    } else if (ServiceDB::httpPorts().contains(m_port)) {
        probeHttp(sock, m_host, m_port, false, opts.readTimeoutMs, r);
    } else if (m_port == 6379) {
        probeRedis(sock, opts.readTimeoutMs, r);
    } else if (m_port == 11211) {
        probeMemcached(sock, opts.readTimeoutMs, r);
    } else if (m_port == 2181) {
        probeZookeeper(sock, opts.readTimeoutMs, r);
    } else if (m_port == 873) {
        probeRsync(sock, opts.readTimeoutMs, r);
    } else if (m_port == 554) {
        probeRtsp(sock, opts.readTimeoutMs, r);
    } else {
        // 被动等待 Banner
        const int waitMs = ServiceDB::passivePorts().contains(m_port) ? 1500 : 800;
        const QByteArray raw = readAvailable(sock, waitMs);
        if (m_port == 3306 && !raw.isEmpty()) {
            parseMysqlHandshake(raw, r);
        } else {
            const QString text = sanitize(raw);
            if (!text.isEmpty())
                r.banner = text;
        }

        // 非二进制协议且无 Banner：尝试 HTTP 兜底（大量非标 Web 服务靠它识别）
        if (r.banner.isEmpty() && r.product.isEmpty() && !isBinaryOnlyPort(m_port)
            && m_port != 3306) {
            probeHttp(sock, m_host, m_port, false,
                      qMin(2500, opts.readTimeoutMs), r);
            if (!r.httpStatus) {
                r.httpStatus = 0;
                r.httpHeaders.clear();
                r.httpBody.clear();
                r.httpTitle.clear();
                r.httpServer.clear();
                r.httpPoweredBy.clear();
                r.service = r.serviceHint;
            }
        }
    }

    delete sock;
    submitResult(r, m_ctx);
}

// ---------------------------------------------------------------------------
// HostPingTask
// ---------------------------------------------------------------------------

HostPingTask::HostPingTask(const QString &host, const QString &label, ScanContext *ctx)
    : m_host(host), m_label(label), m_ctx(ctx)
{
    setAutoDelete(true);
}

void HostPingTask::run()
{
    if (canceled(m_ctx)) return;
    static const quint16 pingPorts[] = {80, 443, 22, 445, 3389};
    for (quint16 p : pingPorts) {
        if (canceled(m_ctx)) return;
        QTcpSocket s;
        s.connectToHost(m_host, p);
        if (s.waitForConnected(900)) {
            s.disconnectFromHost();
            QMutexLocker lock(&m_ctx->aliveMutex);
            m_ctx->aliveHosts.insert(m_host);
            return;
        }
    }
}
