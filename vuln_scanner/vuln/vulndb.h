#ifndef VULNDB_H
#define VULNDB_H

#include <QObject>
#include <QString>
#include <QStringList>
#include <QVector>
#include <QList>
#include <QRegularExpression>
#include "../model/scantypes.h"

// 指纹规则：用于识别产品与版本（不产生漏洞）
struct FingerprintRule {
    QString product;
    QString service;       // 非空时覆盖 result.service
    QString source;        // banner/server/poweredby/title/body/any
    QRegularExpression regex;
    int versionGroup = 0;
};

// 漏洞规则
struct VulnRule {
    QString id;
    QString title;
    int severity = SevInfo;
    QString product;
    QString service;       // 可选服务过滤
    QList<int> ports;      // 可选端口过滤，空=任意
    QString source;        // banner/server/poweredby/title/body/any/unauth
    QRegularExpression regex;
    int versionGroup = 0;
    QString op;            // any/lt/le/eq/gte
    QString version;       // 比较基准版本
    QString description;
    QString solution;
    QString reference;
};

// 规则化漏洞库：内置 JSON 规则 + 代码内置检查
// 只读对象，多线程并发 match 是安全的。
class VulnDb
{
public:
    VulnDb();

    // 加载规则：优先 qrc 内置，再合并应用目录下的 vuln_rules.json
    // 返回错误信息（空串=成功）
    QString load();

    int ruleCount() const { return m_vulnRules.size(); }

    // 用指纹规则填充 result.product / version / service
    void identify(PortResult &r) const;

    // 对一个开放端口执行全部检查，返回命中的漏洞
    QVector<VulnFinding> match(const PortResult &r) const;

    // 点状版本号比较：a < b 返回 -1；相等 0；a > b 1
    static int versionCompare(const QString &a, const QString &b);
    static QString normalizeVersion(const QString &v);

private:
    QString loadFromFile(const QString &path, bool builtin);
    void parseRules(const QJsonDocument &doc);
    QVector<VulnFinding> matchJsonRules(const PortResult &r) const;
    QVector<VulnFinding> builtinChecks(const PortResult &r) const;

    static QString sourceText(const PortResult &r, const QString &source);

    QVector<FingerprintRule> m_fps;
    QVector<VulnRule> m_vulnRules;
};

#endif // VULNDB_H
