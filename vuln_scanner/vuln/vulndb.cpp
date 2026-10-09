#include "vulndb.h"

#include <QFile>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QCoreApplication>
#include <QRegularExpression>

static int severityFromString(const QString &s)
{
    const QString t = s.toLower();
    if (t == QLatin1String("critical")) return SevCritical;
    if (t == QLatin1String("high"))     return SevHigh;
    if (t == QLatin1String("medium"))   return SevMedium;
    if (t == QLatin1String("low"))      return SevLow;
    return SevInfo;
}

VulnDb::VulnDb() {}

QString VulnDb::load()
{
    // 1) 内置规则
    QFile f(QStringLiteral(":/rules/vuln_rules.json"));
    if (!f.open(QIODevice::ReadOnly))
        return QStringLiteral("内置规则库无法打开");
    QJsonParseError pe;
    QJsonDocument doc = QJsonDocument::fromJson(f.readAll(), &pe);
    f.close();
    if (pe.error != QJsonParseError::NoError)
        return QStringLiteral("内置规则库解析失败: ") + pe.errorString();
    parseRules(doc);

    // 2) 应用目录外部覆盖/扩展（可选）
    const QString external = QCoreApplication::applicationDirPath()
                             + QStringLiteral("/vuln_rules.json");
    if (QFile::exists(external))
        loadFromFile(external, false);

    return QString();
}

QString VulnDb::loadFromFile(const QString &path, bool /*builtin*/)
{
    QFile f(path);
    if (!f.open(QIODevice::ReadOnly))
        return QStringLiteral("无法打开 ") + path;
    QJsonParseError pe;
    QJsonDocument doc = QJsonDocument::fromJson(f.readAll(), &pe);
    if (pe.error != QJsonParseError::NoError)
        return path + QStringLiteral(" 解析失败: ") + pe.errorString();
    parseRules(doc);
    return QString();
}

void VulnDb::parseRules(const QJsonDocument &doc)
{
    const QJsonObject root = doc.object();

    const QJsonArray fps = root.value(QStringLiteral("fingerprints")).toArray();
    for (const QJsonValue &v : fps) {
        const QJsonObject o = v.toObject();
        FingerprintRule fp;
        fp.product = o.value(QStringLiteral("product")).toString();
        fp.service = o.value(QStringLiteral("service")).toString();
        fp.source = o.value(QStringLiteral("source")).toString(QStringLiteral("any"));
        fp.versionGroup = o.value(QStringLiteral("versionGroup")).toInt(0);
        fp.regex = QRegularExpression(o.value(QStringLiteral("regex")).toString());
        if (fp.regex.isValid() && !fp.product.isEmpty())
            m_fps.append(fp);
    }

    const QJsonArray rules = root.value(QStringLiteral("rules")).toArray();
    for (const QJsonValue &v : rules) {
        const QJsonObject o = v.toObject();
        VulnRule r;
        r.id = o.value(QStringLiteral("id")).toString();
        r.title = o.value(QStringLiteral("title")).toString();
        r.product = o.value(QStringLiteral("product")).toString();
        r.service = o.value(QStringLiteral("service")).toString();
        r.source = o.value(QStringLiteral("source")).toString(QStringLiteral("any"));
        r.op = o.value(QStringLiteral("op")).toString(QStringLiteral("any"));
        r.version = o.value(QStringLiteral("version")).toString();
        r.versionGroup = o.value(QStringLiteral("versionGroup")).toInt(1);
        r.severity = severityFromString(o.value(QStringLiteral("severity")).toString());
        r.description = o.value(QStringLiteral("description")).toString();
        r.solution = o.value(QStringLiteral("solution")).toString();
        r.reference = o.value(QStringLiteral("reference")).toString();
        const QJsonArray ports = o.value(QStringLiteral("ports")).toArray();
        for (const QJsonValue &p : ports)
            r.ports.append(p.toInt());
        r.regex = QRegularExpression(o.value(QStringLiteral("regex")).toString(),
                                     QRegularExpression::CaseInsensitiveOption);
        if (r.regex.isValid() && !r.id.isEmpty())
            m_vulnRules.append(r);
    }
}

QString VulnDb::sourceText(const PortResult &r, const QString &source)
{
    const QString s = source.toLower();
    if (s == QLatin1String("banner"))    return r.banner;
    if (s == QLatin1String("server"))    return r.httpServer;
    if (s == QLatin1String("poweredby")) return r.httpPoweredBy;
    if (s == QLatin1String("title"))     return r.httpTitle;
    if (s == QLatin1String("body"))      return r.httpBody;
    if (s == QLatin1String("unauth"))    return r.unauth ? QStringLiteral("unauth") : QString();
    // any：全部拼接，扩大命中面（含所有响应头，形如 key: value）
    QString any = r.banner + QLatin1Char('\n') + r.httpServer + QLatin1Char('\n')
                  + r.httpPoweredBy + QLatin1Char('\n') + r.httpTitle + QLatin1Char('\n')
                  + r.httpBody;
    for (auto it = r.httpHeaders.constBegin(); it != r.httpHeaders.constEnd(); ++it)
        any += QLatin1Char('\n') + it.key() + QStringLiteral(": ") + it.value();
    return any;
}

void VulnDb::identify(PortResult &r) const
{
    for (const FingerprintRule &fp : m_fps) {
        const bool httpCtx = r.httpStatus > 0 && fp.source != QLatin1String("banner");
        if (!fp.service.isEmpty() && !httpCtx
            && !r.service.contains(fp.service, Qt::CaseInsensitive)
            && !r.serviceHint.contains(fp.service, Qt::CaseInsensitive))
            continue;
        const QString text = sourceText(r, fp.source);
        if (text.isEmpty())
            continue;
        QRegularExpressionMatch m = fp.regex.match(text);
        if (m.hasMatch()) {
            r.product = fp.product;
            if (fp.versionGroup > 0 && fp.versionGroup <= m.lastCapturedIndex())
                r.version = m.captured(fp.versionGroup);
            if (!fp.service.isEmpty())
                r.service = fp.service;
            return; // 第一个高置信指纹优先
        }
    }
}

QVector<VulnFinding> VulnDb::match(const PortResult &r) const
{
    QVector<VulnFinding> out;
    if (!r.open)
        return out;
    out = matchJsonRules(r);
    const QVector<VulnFinding> built = builtinChecks(r);
    for (const VulnFinding &f : built)
        out.append(f);
    return out;
}

QVector<VulnFinding> VulnDb::matchJsonRules(const PortResult &r) const
{
    QVector<VulnFinding> out;
    for (const VulnRule &rule : m_vulnRules) {
        if (!rule.ports.isEmpty() && !rule.ports.contains(static_cast<int>(r.port)))
            continue;
        if (!rule.service.isEmpty()) {
            const bool httpCtx = r.httpStatus > 0
                                 && rule.source != QLatin1String("banner");
            const QString svc = r.service.isEmpty() ? r.serviceHint : r.service;
            if (!httpCtx && !svc.contains(rule.service, Qt::CaseInsensitive))
                continue;
        }

        const QString text = sourceText(r, rule.source);
        if (text.isEmpty())
            continue;
        QRegularExpressionMatch m = rule.regex.match(text);
        if (!m.hasMatch())
            continue;

        QString detected = r.version;
        if (rule.versionGroup > 0 && rule.versionGroup <= m.lastCapturedIndex())
            detected = m.captured(rule.versionGroup);

        if (!rule.version.isEmpty() && !rule.op.isEmpty()
            && rule.op != QLatin1String("any")) {
            if (detected.trimmed().isEmpty())
                continue;
            const int cmp = versionCompare(detected, rule.version);
            bool hit = false;
            if (rule.op == QLatin1String("lt"))  hit = cmp < 0;
            if (rule.op == QLatin1String("le"))  hit = cmp <= 0;
            if (rule.op == QLatin1String("eq"))  hit = cmp == 0;
            if (rule.op == QLatin1String("gte")) hit = cmp >= 0;
            if (!hit)
                continue;
        }

        VulnFinding f;
        f.ruleId = rule.id;
        f.title = rule.title;
        f.severity = rule.severity;
        f.product = rule.product;
        f.version = detected;
        f.description = rule.description;
        f.solution = rule.solution;
        f.reference = rule.reference;
        out.append(f);
    }
    return out;
}

// 代码内置检查：端口暴露类、未授权访问类、HTTP 安全配置类
QVector<VulnFinding> VulnDb::builtinChecks(const PortResult &r) const
{
    QVector<VulnFinding> f;
    auto add = [&](int sev, const QString &id, const QString &title,
                   const QString &desc, const QString &fix,
                   const QString &ref = QString()) {
        VulnFinding v;
        v.severity = sev; v.ruleId = id; v.title = title;
        v.description = desc; v.solution = fix; v.reference = ref;
        v.product = r.product.isEmpty() ? r.serviceHint : r.product;
        v.version = r.version;
        f.append(v);
    };

    const QString svc = r.service.isEmpty() ? r.serviceHint : r.service;
    const bool isHttp = svc.contains(QStringLiteral("http"), Qt::CaseInsensitive)
                        || r.httpStatus > 0;

    // ---- 未授权访问（由主动探测在 PortTask 中确认） ----
    if (r.unauth) {
        if (svc.contains(QStringLiteral("redis")))
            add(SevHigh, "VS-REDIS-UNAUTH", QStringLiteral("Redis 未授权访问"),
                QStringLiteral("Redis 无需认证即可访问，攻击者可读写数据、写入 SSH 公钥或计划任务实现远程控制。"),
                QStringLiteral("绑定内网地址；配置 requirepass；以低权限账号运行；禁止 root 运行。"));
        else if (svc.contains(QStringLiteral("docker")))
            add(SevCritical, "VS-DOCKER-UNAUTH", QStringLiteral("Docker Remote API 未授权访问"),
                QStringLiteral("2375 端口 Docker API 无认证，可通过挂载宿主机根目录直接接管服务器。"),
                QStringLiteral("禁止公网暴露 2375；改用 TLS(2376) 或本地 unix sock；配置防火墙白名单。"));
        else if (svc.contains(QStringLiteral("elasticsearch")))
            add(SevHigh, "VS-ES-UNAUTH", QStringLiteral("Elasticsearch 未授权访问"),
                QStringLiteral("9200 端口无需认证即可读取集群、索引与业务数据。"),
                QStringLiteral("开启 X-Pack Security / Search Guard；绑定内网；增加访问控制。"));
        else if (svc.contains(QStringLiteral("kubelet")))
            add(SevCritical, "VS-KUBELET-UNAUTH", QStringLiteral("Kubelet 只读/读写端口未授权"),
                QStringLiteral("10250/10255 可未授权获取 Pod、容器信息甚至执行命令。"),
                QStringLiteral("关闭只读端口，启用 Webhook 授权与匿名访问禁用；限制网络访问。"));
        else if (svc.contains(QStringLiteral("zookeeper")))
            add(SevHigh, "VS-ZK-UNAUTH", QStringLiteral("ZooKeeper 未授权访问"),
                QStringLiteral("mntr/envi/conf 四字命令可泄露集群与环境敏感信息，envi 可泄露环境变量。"),
                QStringLiteral("升级并配置 4lw.commands.whitelist 为空；绑定内网；增加 ACL。"));
        else if (svc.contains(QStringLiteral("memcached")))
            add(SevMedium, "VS-MC-UNAUTH", QStringLiteral("Memcached 未授权访问"),
                QStringLiteral("可未授权读取缓存数据，UDP 模式还可被用于反射放大攻击。"),
                QStringLiteral("绑定内网；使用 -l 限定监听；配置 SASL 认证或防火墙。"));
        else if (svc.contains(QStringLiteral("nacos")))
            add(SevCritical, "VS-NACOS-UNAUTH", QStringLiteral("Nacos 未授权/默认密钥"),
                QStringLiteral("可未授权读取用户列表或利用默认 JWT 密钥伪造身份，进而接管配置中心。"),
                QStringLiteral("开启鉴权并修改 nacos.core.auth.default.token.secret.key；升级到最新版本；限制访问。"));
        else if (svc.contains(QStringLiteral("couchdb")))
            add(SevHigh, "VS-COUCHDB-UNAUTH", QStringLiteral("CouchDB 未授权访问"),
                QStringLiteral("可未授权读写数据库，并可能利用垂直越权创建管理员。"),
                QStringLiteral("绑定内网；启用认证管理员(require_valid_user)；升级到 1.7.2/2.x 最新版。"));
        else if (svc.contains(QStringLiteral("rsync")))
            add(SevMedium, "VS-RSYNC-MODULES", QStringLiteral("rsync 模块信息泄露"),
                QStringLiteral("rsyncd 可匿名枚举共享模块，可能允许匿名拉取敏感文件。"),
                QStringLiteral("配置 auth users 与 secrets file；限制 hosts allow；不要以 root 运行。"));
        else if (isHttp)
            add(SevMedium, "VS-WEB-OPEN", QStringLiteral("管理/中间件 Web 控制台可直接访问"),
                QStringLiteral("该 Web 服务无需认证即可访问，可能是中间件管理控制台。"),
                QStringLiteral("增加认证、限制来源 IP、绑定内网或放置于 VPN 之后。"));
    }

    // ---- 高风险服务暴露 ----
    switch (r.port) {
    case 23:
        add(SevHigh, "VS-TELNET", QStringLiteral("Telnet 明文远程管理"),
            QStringLiteral("Telnet 以明文传输账号口令，易被嗅探与中间人攻击，网络设备上尤为常见。"),
            QStringLiteral("关闭 Telnet，改用 SSH；在网络设备上使用 SSHv2 并限制管理来源。"));
        break;
    case 512: case 513: case 514:
        add(SevHigh, "VS-RSERVICES", QStringLiteral("rlogin/rsh/rexec 信任服务暴露"),
            QStringLiteral("r 系列服务基于 IP 信任、明文传输且历史上漏洞众多。"),
            QStringLiteral("关闭 rexec/rlogin/rsh，使用 SSH 替代。"));
        break;
    case 111:
        add(SevLow, "VS-RPCBIND", QStringLiteral("rpcbind/portmap 暴露"),
            QStringLiteral("可枚举 NFS 等 RPC 服务信息，辅助攻击者发现内网服务。"),
            QStringLiteral("不需要 NFS 时关闭 rpcbind；限制 TCP/111 的访问来源。"));
        break;
    case 2049:
        add(SevMedium, "VS-NFS", QStringLiteral("NFS 服务暴露"),
            QStringLiteral("配置不当时可匿名挂载共享目录，导致数据泄露。"),
            QStringLiteral("限制 exports 网段与 no_root_squash 配置；启用 Kerberos 认证。"));
        break;
    case 2323:
        add(SevHigh, "VS-2323", QStringLiteral("2323 端口暴露（常见为设备 Telnet/后门）"),
            QStringLiteral("多家安防设备在该端口提供 Telnet 或出厂调试服务。"),
            QStringLiteral("确认设备固件，关闭调试端口并升级固件。"));
        break;
    case 4786:
        add(SevCritical, "VS-CISCO-SMI", QStringLiteral("Cisco Smart Install 暴露"),
            QStringLiteral("Smart Install 无认证，攻击者可篡改设备配置、替换 IOS 镜像。"),
            QStringLiteral("停用 Smart Install(vstack)；通过 ACL 阻断 TCP 4786；升级固件。"),
            QStringLiteral("https://sec.cloudapps.cisco.com/security/center/content/CiscoSecurityAdvisory/cisco-sr-20170214-smi"));
        break;
    case 8009:
        add(SevHigh, "CVE-2020-1938", QStringLiteral("Tomcat AJP Ghostcat 文件包含"),
            QStringLiteral("AJP 8009 暴露时，未修复版本(9.0.31/8.5.51/7.0.100 之前)可被利用读取 WEB-INF 下文件，配合上传可 RCE。"),
            QStringLiteral("升级 Tomcat 至 9.0.31+/8.5.51+/7.0.100+；或在 server.xml 中禁用 AJP 连接器。"));
        break;
    case 6379:
        if (!r.unauth)
            add(SevLow, "VS-REDIS-EXPOSED", QStringLiteral("Redis 端口对外暴露"),
                QStringLiteral("即使配置了密码，暴露 6379 也会增加被爆破与利用的面。"),
                QStringLiteral("绑定 127.0.0.1/内网，通过防火墙限制来源。"));
        break;
    case 3389:
        add(SevMedium, "CVE-2019-0708", QStringLiteral("RDP 暴露（需确认 BlueKeep 补丁）"),
            QStringLiteral("远程桌面直接暴露时，未打补丁的 Windows 可能受 BlueKeep 蠕虫级漏洞影响（需进一步验证补丁状态）。"),
                QStringLiteral("启用 NLA；安装 2019-05 安全更新；用防火墙/堡垒机限制 3389 来源。"),
                QStringLiteral("https://msrc.microsoft.com/update-guide/vulnerability/CVE-2019-0708"));
        break;
    case 445:
        add(SevMedium, "MS17-010", QStringLiteral("SMB 暴露（需确认 MS17-010 补丁）"),
            QStringLiteral("445 端口暴露时，未安装 MS17-010 的主机可能被 EternalBlue 远程利用（需进一步验证）。"),
            QStringLiteral("安装 MS17-010；禁用 SMBv1；阻断外网 445 访问。"));
        break;
    case 5900:
        add(SevMedium, "VS-VNC", QStringLiteral("VNC 远程桌面暴露"),
            QStringLiteral("VNC 认证强度弱、历史口令可被暴力破解，老旧实现存在多个漏洞。"),
            QStringLiteral("限制访问来源；使用强口令；通过 SSH 隧道访问；升级 VNC 版本。"));
        break;
    case 37777: case 34567: case 34568:
        add(SevMedium, "VS-DVR-NATIVE", QStringLiteral("安防设备私有协议端口暴露"),
            QStringLiteral("DVR/NVR 私有服务端口暴露，老旧固件存在未授权访问与硬编码凭据问题。"),
            QStringLiteral("升级设备固件；禁止映射到公网；修改默认口令。"));
        break;
    case 6000:
        add(SevHigh, "VS-X11", QStringLiteral("X11 显示服务暴露"),
            QStringLiteral("配置不当的 X11 允许远程读取屏幕、注入键盘事件。"),
            QStringLiteral("关闭 TCP 监听(-nolisten tcp)；使用 xauth；防火墙阻断 6000。"));
        break;
    default: break;
    }

    // ---- 明文协议 ----
    if (r.port == 21 && !r.tls)
        add(SevLow, "VS-FTP-PLAINTEXT", QStringLiteral("FTP 明文传输"),
            QStringLiteral("FTP 账号口令与数据均为明文传输。"),
            QStringLiteral("改用 SFTP 或 FTPS。"));
    if ((r.port == 25 || r.port == 110 || r.port == 143) && !r.tls)
        add(SevInfo, "VS-MAIL-PLAINTEXT", QStringLiteral("邮件服务明文端口"),
            QStringLiteral("SMTP/POP3/IMAP 明文端口可被嗅探，建议启用 STARTTLS。"),
            QStringLiteral("启用 STARTTLS 并引导使用 465/993/995 加密端口。"));

    // ---- VNC 协议版本 ----
    if (r.banner.contains(QStringLiteral("RFB 003.003"), Qt::CaseInsensitive))
        add(SevMedium, "VS-VNC-33", QStringLiteral("VNC 使用 3.3 认证协议"),
            QStringLiteral("RFB 3.3 仅支持基于 DES 的弱认证，且无尝试次数限制。"),
            QStringLiteral("升级 VNC 服务端并使用强认证。"));

    // ---- MySQL/MariaDB EOL ----
    if (r.product.compare(QStringLiteral("MySQL"), Qt::CaseInsensitive) == 0
        || r.product.compare(QStringLiteral("MariaDB"), Qt::CaseInsensitive) == 0) {
        const int cmp57 = versionCompare(r.version, QStringLiteral("5.7.0"));
        if (!r.version.isEmpty() && cmp57 < 0)
            add(SevHigh, "VS-MYSQL-EOL", QStringLiteral("%1 版本已停止维护").arg(r.product),
                QStringLiteral("探测到 %1 %2，该分支已 EOL，存在大量未修复安全缺陷。")
                    .arg(r.product, r.version),
                QStringLiteral("升级到官方仍在维护的版本（MySQL 8.0 / MariaDB 最新稳定版）。"));
        else if (r.product.compare(QStringLiteral("MySQL"), Qt::CaseInsensitive) == 0
                 && r.version.startsWith(QStringLiteral("5.7")))
            add(SevLow, "VS-MYSQL-57", QStringLiteral("MySQL 5.7 生命周期接近终止"),
                QStringLiteral("MySQL 5.7 扩展支持已结束，建议规划升级。"),
                QStringLiteral("升级到 MySQL 8.0+，并限制 3306 访问来源。"));
    }

    // ---- HTTP 安全配置类 ----
    if (isHttp && r.httpStatus > 0) {
        const QString body = r.httpBody.toLower();
        if (body.contains(QStringLiteral("index of /"))
            || body.contains(QStringLiteral("directory listing for")))
            add(SevMedium, "VS-DIR-LISTING", QStringLiteral("目录列表开启"),
                QStringLiteral("服务器开启了目录浏览，可枚举目录中的敏感文件。"),
                QStringLiteral("关闭自动索引（Apache Options -Indexes / nginx autoindex off）。"));

        if (r.tls && !r.httpHeaders.contains(QStringLiteral("strict-transport-security")))
            add(SevLow, "VS-HSTS", QStringLiteral("缺少 HSTS 头"),
                QStringLiteral("HTTPS 站点未设置 Strict-Transport-Security，存在降级风险。"),
                QStringLiteral("增加 Strict-Transport-Security: max-age=31536000"));
        if (!r.httpHeaders.contains(QStringLiteral("x-frame-options"))
            && !r.httpHeaders.value(QStringLiteral("content-security-policy"))
                   .contains(QStringLiteral("frame-ancestors"), Qt::CaseInsensitive))
            add(SevLow, "VS-CLICKJACK", QStringLiteral("缺少点击劫持防护头"),
                QStringLiteral("未设置 X-Frame-Options 或 CSP frame-ancestors。"),
                QStringLiteral("增加 X-Frame-Options: SAMEORIGIN 或 CSP frame-ancestors 指令。"));
        static const QRegularExpression digitsVersion(QStringLiteral("\\d+\\.\\d+"));
        if (r.httpServer.contains(digitsVersion))
            add(SevInfo, "VS-SERVER-DISCLOSE", QStringLiteral("HTTP Server 头泄露版本"),
                QStringLiteral("Server 头暴露了精确版本，便于攻击者匹配漏洞。"),
                QStringLiteral("隐藏或简化 Server 头（server_tokens off 等）。"));
    }

    return f;
}

QString VulnDb::normalizeVersion(const QString &v)
{
    QString out;
    for (const QChar c : v.trimmed()) {
        if (c.isDigit())
            out.append(c);
        else
            out.append(QLatin1Char('.'));
    }
    // 合并连续的点
    while (out.contains(QStringLiteral("..")))
        out.replace(QStringLiteral(".."), QStringLiteral("."));
    while (out.startsWith(QLatin1Char('.'))) out.remove(0, 1);
    while (out.endsWith(QLatin1Char('.'))) out.chop(1);
    return out;
}

int VulnDb::versionCompare(const QString &a, const QString &b)
{
    const QStringList la = normalizeVersion(a).split(QLatin1Char('.'), QString::SkipEmptyParts);
    const QStringList lb = normalizeVersion(b).split(QLatin1Char('.'), QString::SkipEmptyParts);
    const int n = qMax(la.size(), lb.size());
    for (int i = 0; i < n; ++i) {
        const quint64 va = i < la.size() ? la[i].toULongLong() : 0;
        const quint64 vb = i < lb.size() ? lb[i].toULongLong() : 0;
        if (va < vb) return -1;
        if (va > vb) return 1;
    }
    return 0;
}
