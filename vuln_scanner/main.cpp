#include <QApplication>
#include <QTextStream>
#include <QStringList>
#include <QElapsedTimer>

#include "mainwindow.h"
#include "core/scanengine.h"
#include "core/servicedb.h"
#include "vuln/vulndb.h"

#ifdef Q_OS_WIN
#include <windows.h>
// Release 构建为 windows 子系统，自检时挂回父进程控制台以输出结果
static void attachParentConsole()
{
    // stdout 已被重定向到文件/管道时不抢占
    if (GetFileType(GetStdHandle(STD_OUTPUT_HANDLE)) != FILE_TYPE_UNKNOWN)
        return;
    if (AttachConsole(ATTACH_PARENT_PROCESS)) {
        freopen("CONOUT$", "w", stdout);
        freopen("CONOUT$", "w", stderr);
    }
}
#endif

// 无头自检: VulnScanner --selftest [目标] [端口规格]
// 用于在无 GUI 环境验证扫描链路，不弹窗、不写文件。
static int runSelfTest(const QStringList &args)
{
#ifdef Q_OS_WIN
    attachParentConsole();
#endif
    QTextStream out(stdout);
    const QString target = args.size() > 2 ? args[2] : QStringLiteral("127.0.0.1");
    QString specErr;
    QVector<quint16> ports = args.size() > 3
        ? ServiceDB::parsePortSpec(args[3], &specErr)
        : ServiceDB::presetTopPorts();
    if (ports.isEmpty()) {
        out << "端口参数无效: " << specErr << "\n";
        return 2;
    }

    VulnDb db;
    const QString dbErr = db.load();
    if (!dbErr.isEmpty()) {
        out << "规则库加载失败: " << dbErr << "\n";
        return 2;
    }
    out << "规则条目: " << db.ruleCount() << "\n";

    ScanOptions opts;
    opts.connectTimeoutMs = 800;
    opts.readTimeoutMs = 1800;
    opts.concurrency = 64;
    opts.hostDiscovery = false;

    ScanEngine engine(&db);
    engine.configure(target, ports, opts);

    int openCount = 0;
    int findingCount = 0;
    QObject::connect(&engine, &ScanEngine::log,
                     [&](const QString &m) { out << m << "\n"; });
    QObject::connect(&engine, &ScanEngine::portFound,
                     [&](const PortResult &r) {
        ++openCount;
        out << "OPEN " << r.host << ":" << r.port
            << "  " << (r.service.isEmpty() ? r.serviceHint : r.service)
            << "  " << r.product << " " << r.version
            << (r.unauth ? "  [UNAUTH]" : "") << "\n";
        if (!r.banner.isEmpty())
            out << "    banner: " << r.banner.left(160) << "\n";
        for (const VulnFinding &f : r.findings) {
            ++findingCount;
            out << "    [" << severityText(f.severity) << "] "
                << f.ruleId << " " << f.title << "\n";
        }
    });
    QObject::connect(&engine, &ScanEngine::progress,
                     [&](int done, int total) {
        if (done == total || done % 500 == 0)
            out << "progress " << done << "/" << total << "\n";
    });

    QElapsedTimer t;
    t.start();
    engine.start(); // 直接在当前线程同步执行
    out << "selftest 完成: 开放端口 " << openCount
        << ", 漏洞命中 " << findingCount
        << ", 耗时 " << (t.elapsed() / 1000.0) << "s\n";
    return 0;
}

int main(int argc, char *argv[])
{
    QApplication app(argc, argv);
    QApplication::setApplicationName(QStringLiteral("VulnScanner"));
    QApplication::setOrganizationName(QStringLiteral("TraeLabs"));

    qRegisterMetaType<PortResult>("PortResult");
    qRegisterMetaType<VulnFinding>("VulnFinding");
    qRegisterMetaType<ScanSummary>("ScanSummary");

    const QStringList args = QApplication::arguments();
    if (args.contains(QStringLiteral("--selftest")))
        return runSelfTest(args);

    MainWindow w;
    w.show();
    return app.exec();
}
