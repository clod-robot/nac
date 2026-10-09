#ifndef REPORTEXPORT_H
#define REPORTEXPORT_H

#include <QString>
#include <QMap>
#include "../model/scantypes.h"

// 扫描结果报告导出（HTML / CSV），纯 Qt 实现
class ReportExport
{
public:
    static bool exportHtml(const QString &path, const QMap<QString, HostResult> &hosts,
                           const ScanSummary &summary, const QString &targetText,
                           QString *err = nullptr);
    static bool exportCsv(const QString &path, const QMap<QString, HostResult> &hosts,
                          QString *err = nullptr);
};

#endif // REPORTEXPORT_H
