#include "reportexport.h"

#include <QFile>
#include <QTextStream>
#include <QDateTime>
#include <QSet>

static QString sevColor(int s)
{
    switch (s) {
    case SevCritical: return QStringLiteral("#b71c1c");
    case SevHigh:     return QStringLiteral("#e64a19");
    case SevMedium:   return QStringLiteral("#f9a825");
    case SevLow:      return QStringLiteral("#0277bd");
    default:          return QStringLiteral("#616161");
    }
}

static QString esc(const QString &s)
{
    QString r = s.toHtmlEscaped();
    return r;
}

bool ReportExport::exportHtml(const QString &path, const QMap<QString, HostResult> &hosts,
                              const ScanSummary &summary, const QString &targetText,
                              QString *err)
{
    QFile f(path);
    if (!f.open(QIODevice::WriteOnly | QIODevice::Truncate)) {
        if (err) *err = QStringLiteral("无法写入文件: ") + path;
        return false;
    }
    QTextStream out(&f);
    out.setCodec("UTF-8");

    out << QStringLiteral(
        "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
        "<title>漏洞扫描报告</title><style>"
        "body{font-family:'Microsoft YaHei',Arial,sans-serif;margin:24px;color:#222;background:#fafafa;}"
        "h1{font-size:22px;}h2{font-size:17px;margin-top:28px;border-left:4px solid #1565c0;padding-left:8px;}"
        ".cards{display:flex;gap:10px;flex-wrap:wrap;margin:14px 0;}"
        ".card{background:#fff;border:1px solid #e0e0e0;border-radius:8px;padding:10px 16px;min-width:90px;text-align:center;}"
        ".card .n{font-size:22px;font-weight:bold;}.card .l{font-size:12px;color:#757575;}"
        "table{border-collapse:collapse;width:100%;background:#fff;font-size:13px;}"
        "th,td{border:1px solid #e0e0e0;padding:6px 8px;text-align:left;vertical-align:top;}"
        "th{background:#eceff1;}.sev{color:#fff;padding:1px 8px;border-radius:4px;font-size:12px;white-space:nowrap;}"
        ".muted{color:#757575;font-size:12px;}.banner{font-family:Consolas,monospace;background:#f5f5f5;padding:4px;word-break:break-all;}"
        ".fix{color:#2e7d32;}.meta{background:#fff;border:1px solid #e0e0e0;border-radius:8px;padding:10px 16px;font-size:13px;}"
        "</style></head><body>");

    out << QStringLiteral("<h1>服务器与网络设备漏洞扫描报告</h1>");
    out << QStringLiteral("<div class=\"meta\">生成时间：%1<br>扫描目标：%2<br>"
                          "声明：本报告仅针对已获授权的目标，结果基于版本特征匹配，需结合人工验证。</div>")
           .arg(QDateTime::currentDateTime().toString(QStringLiteral("yyyy-MM-dd HH:mm:ss")),
                esc(targetText));

    out << QStringLiteral("<div class=\"cards\">");
    const QPair<QString, int> cards[] = {
        {QStringLiteral("目标主机"), summary.hostsTotal},
        {QStringLiteral("存活"), summary.hostsUp},
        {QStringLiteral("开放端口"), summary.openPorts},
        {QStringLiteral("严重"), summary.critical},
        {QStringLiteral("高危"), summary.high},
        {QStringLiteral("中危"), summary.medium},
        {QStringLiteral("低危"), summary.low},
        {QStringLiteral("信息"), summary.info}
    };
    for (const auto &c : cards)
        out << QStringLiteral("<div class=\"card\"><div class=\"n\">%1</div>"
                              "<div class=\"l\">%2</div></div>")
               .arg(c.second).arg(c.first);
    out << QStringLiteral("</div>");

    // 漏洞汇总表
    out << QStringLiteral("<h2>漏洞清单</h2><table><tr>"
                          "<th>级别</th><th>编号</th><th>目标</th><th>端口/服务</th>"
                          "<th>产品/版本</th><th>漏洞</th><th>修复建议</th></tr>");
    for (const HostResult &h : hosts) {
        for (const PortResult &p : h.ports) {
            if (p.findings.isEmpty()) continue;
            for (const VulnFinding &v : p.findings) {
                QString row = QStringLiteral(
                    "<tr><td><span class=\"sev\" style=\"background:%11\">%10</span></td>"
                    "<td>%9</td><td>%8</td><td>%7 / %6</td>"
                    "<td>%5 %4</td><td><b>%3</b><br><span class=\"muted\">%2</span></td>"
                    "<td class=\"fix\">%1</td></tr>");
                // 从大序号向小序号做字面替换，避免 %1 误伤 %10/%11
                row.replace(QStringLiteral("%11"), sevColor(v.severity));
                row.replace(QStringLiteral("%10"), severityText(v.severity));
                row.replace(QStringLiteral("%9"), esc(v.ruleId));
                row.replace(QStringLiteral("%8"), esc(h.address));
                row.replace(QStringLiteral("%7"), QString::number(p.port));
                row.replace(QStringLiteral("%6"),
                            esc(p.service.isEmpty() ? p.serviceHint : p.service));
                row.replace(QStringLiteral("%5"),
                            esc(v.product.isEmpty() ? p.product : v.product));
                row.replace(QStringLiteral("%4"), esc(v.version));
                row.replace(QStringLiteral("%3"), esc(v.title));
                row.replace(QStringLiteral("%2"), esc(v.description));
                row.replace(QStringLiteral("%1"), esc(v.solution));
                out << row;
            }
        }
    }
    out << QStringLiteral("</table>");

    // 端口资产明细
    out << QStringLiteral("<h2>资产与开放端口明细</h2>");
    for (const HostResult &h : hosts) {
        if (h.ports.isEmpty()) continue;
        out << QStringLiteral("<h3>%1</h3><table><tr><th>端口</th><th>服务</th>"
                              "<th>产品</th><th>版本</th><th>延迟(ms)</th>"
                              "<th>Banner / 标题</th><th>命中</th></tr>")
               .arg(esc(h.address));
        for (const PortResult &p : h.ports) {
            QString detail = p.banner;
            if (!p.httpTitle.isEmpty())
                detail += QStringLiteral(" | title=") + p.httpTitle;
            if (!p.httpServer.isEmpty())
                detail += QStringLiteral(" | server=") + p.httpServer;
            QString row = QStringLiteral(
                "<tr><td>%7</td><td>%6</td><td>%5</td><td>%4</td>"
                "<td>%3</td><td class=\"banner\">%2</td><td>%1</td></tr>");
            row.replace(QStringLiteral("%7"), QString::number(p.port));
            row.replace(QStringLiteral("%6"),
                        esc(p.service.isEmpty() ? p.serviceHint : p.service));
            row.replace(QStringLiteral("%5"), esc(p.product));
            row.replace(QStringLiteral("%4"), esc(p.version));
            row.replace(QStringLiteral("%3"), QString::number(p.latencyMs));
            row.replace(QStringLiteral("%2"), esc(detail.left(400)));
            row.replace(QStringLiteral("%1"), QString::number(p.findings.size()));
            out << row;
        }
        out << QStringLiteral("</table>");
    }

    out << QStringLiteral("<p class=\"muted\">VulnScanner 生成 · 规则化非侵入式探测（TCP Banner / HTTP 特征），"
                          "未包含暴力破解与漏洞利用。</p>");
    out << QStringLiteral("</body></html>");
    out.flush();
    f.close();
    return true;
}

bool ReportExport::exportCsv(const QString &path, const QMap<QString, HostResult> &hosts,
                             QString *err)
{
    QFile f(path);
    if (!f.open(QIODevice::WriteOnly | QIODevice::Truncate)) {
        if (err) *err = QStringLiteral("无法写入文件: ") + path;
        return false;
    }
    QTextStream out(&f);
    out.setCodec("UTF-8");
    out.setGenerateByteOrderMark(true);
    out << QStringLiteral("主机,端口,服务,产品,版本,风险级别,漏洞编号,漏洞名称,修复建议\r\n");

    auto csv = [](const QString &s) {
        QString t = s;
        t.replace(QLatin1Char('"'), QStringLiteral("\"\""));
        t.replace(QLatin1Char('\r'), QStringLiteral(" "));
        t.replace(QLatin1Char('\n'), QStringLiteral(" "));
        return QStringLiteral("\"") + t + QStringLiteral("\"");
    };

    for (const HostResult &h : hosts) {
        for (const PortResult &p : h.ports) {
            if (p.findings.isEmpty()) {
                out << csv(h.address) << "," << p.port << ","
                    << csv(p.service.isEmpty() ? p.serviceHint : p.service) << ","
                    << csv(p.product) << "," << csv(p.version) << ",,,,,\r\n";
                continue;
            }
            for (const VulnFinding &v : p.findings) {
                out << csv(h.address) << "," << p.port << ","
                    << csv(p.service.isEmpty() ? p.serviceHint : p.service) << ","
                    << csv(v.product.isEmpty() ? p.product : v.product) << ","
                    << csv(v.version) << ","
                    << csv(severityText(v.severity)) << ","
                    << csv(v.ruleId) << "," << csv(v.title) << ","
                    << csv(v.solution) << "\r\n";
            }
        }
    }
    out.flush();
    f.close();
    return true;
}
