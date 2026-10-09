#include "mainwindow.h"
#include "core/scanengine.h"
#include "core/servicedb.h"
#include "report/reportexport.h"

#include <QApplication>
#include <QFont>
#include <QColor>
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGridLayout>
#include <QGroupBox>
#include <QLabel>
#include <QPlainTextEdit>
#include <QLineEdit>
#include <QComboBox>
#include <QSpinBox>
#include <QCheckBox>
#include <QPushButton>
#include <QProgressBar>
#include <QTreeWidget>
#include <QTableWidget>
#include <QTextBrowser>
#include <QTabWidget>
#include <QSplitter>
#include <QHeaderView>
#include <QMessageBox>
#include <QFileDialog>
#include <QMenuBar>
#include <QMenu>
#include <QAction>
#include <QStatusBar>
#include <QSettings>
#include <QFileInfo>
#include <QDateTime>
#include <QSslSocket>

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

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent)
{
    const QString dbErr = m_db.load();

    buildUi();

    if (!dbErr.isEmpty())
        QMessageBox::warning(this, QStringLiteral("规则库"), dbErr);

    // 首次启动法律声明
    QSettings st;
    if (!st.value(QStringLiteral("legalAccepted"), false).toBool()) {
        const QString note =
            QStringLiteral("本工具仅用于对【已获得合法书面授权】的服务器与网络设备进行安全评估。\n\n"
                           "工具采用非侵入式探测（TCP 连接、Banner/HTTP 特征识别、公开 CVE 版本匹配），"
                           "不包含暴力破解与漏洞利用功能。\n\n"
                           "未经授权扫描第三方系统可能违反《网络安全法》等法律法规，"
                           "请在允许的范围内使用并自行承担责任。");
        const auto ret = QMessageBox::warning(this, QStringLiteral("使用声明与免责"),
                                              note, QMessageBox::Ok | QMessageBox::Cancel);
        if (ret == QMessageBox::Ok)
            st.setValue(QStringLiteral("legalAccepted"), true);
        else
            QApplication::exit(0);
    }
}

void MainWindow::buildUi()
{
    setWindowTitle(QStringLiteral("服务器与网络设备漏洞扫描工具  v1.0"));
    resize(1180, 760);

    auto *central = new QWidget;
    auto *root = new QVBoxLayout(central);

    // ---------------- 配置区 ----------------
    auto *cfgBox = new QGroupBox(QStringLiteral("扫描配置"));
    auto *cfg = new QGridLayout(cfgBox);

    cfg->addWidget(new QLabel(QStringLiteral("目标 (IP / CIDR / 范围 / 域名，换行或逗号分隔):")), 0, 0, 1, 4);
    m_targetsEdit = new QPlainTextEdit;
    m_targetsEdit->setPlainText(QStringLiteral("127.0.0.1"));
    m_targetsEdit->setMaximumHeight(70);
    cfg->addWidget(m_targetsEdit, 1, 0, 1, 4);

    cfg->addWidget(new QLabel(QStringLiteral("端口预设:")), 2, 0);
    m_presetCombo = new QComboBox;
    m_presetCombo->addItems({QStringLiteral("常用端口 (~38)"),
                             QStringLiteral("标准端口 (~100)"),
                             QStringLiteral("Web / 中间件"),
                             QStringLiteral("网络设备 / 安防"),
                             QStringLiteral("全部端口 (1-65535，慢)"),
                             QStringLiteral("自定义")});
    cfg->addWidget(m_presetCombo, 2, 1);

    m_customPortsEdit = new QLineEdit;
    m_customPortsEdit->setPlaceholderText(QStringLiteral("如 22,80,443,3306,8000-8100"));
    m_customPortsEdit->setEnabled(false);
    cfg->addWidget(new QLabel(QStringLiteral("自定义端口:")), 2, 2);
    cfg->addWidget(m_customPortsEdit, 2, 3);

    cfg->addWidget(new QLabel(QStringLiteral("连接超时(ms):")), 3, 0);
    m_timeoutSpin = new QSpinBox;
    m_timeoutSpin->setRange(300, 10000);
    m_timeoutSpin->setSingleStep(100);
    m_timeoutSpin->setValue(2000);
    cfg->addWidget(m_timeoutSpin, 3, 1);

    cfg->addWidget(new QLabel(QStringLiteral("并发数:")), 3, 2);
    m_concurrencySpin = new QSpinBox;
    m_concurrencySpin->setRange(1, 1000);
    m_concurrencySpin->setValue(100);
    cfg->addWidget(m_concurrencySpin, 3, 3);

    m_discoveryCheck = new QCheckBox(QStringLiteral("先做主机存活探测 (TCP Ping，扫网段时建议勾选)"));
    m_discoveryCheck->setChecked(true);
    cfg->addWidget(m_discoveryCheck, 4, 0, 1, 2);

    auto *btnRow = new QHBoxLayout;
    m_startBtn = new QPushButton(QStringLiteral("开始扫描"));
    m_stopBtn = new QPushButton(QStringLiteral("停止"));
    m_stopBtn->setEnabled(false);
    m_startBtn->setStyleSheet(QStringLiteral("padding:6px 24px;font-weight:bold;"));
    btnRow->addWidget(m_startBtn);
    btnRow->addWidget(m_stopBtn);
    btnRow->addStretch();
    cfg->addLayout(btnRow, 4, 2, 1, 2);

    root->addWidget(cfgBox);

    // ---------------- 进度条 ----------------
    auto *progRow = new QHBoxLayout;
    m_progress = new QProgressBar;
    m_progress->setTextVisible(true);
    m_progress->setFormat(QStringLiteral("就绪"));
    m_statLabel = new QLabel;
    progRow->addWidget(m_progress, 3);
    progRow->addWidget(m_statLabel, 2);
    root->addLayout(progRow);

    // ---------------- 结果区 ----------------
    auto *tabs = new QTabWidget;

    // 资产树 + 详情
    auto *assetTab = new QWidget;
    auto *assetLayout = new QVBoxLayout(assetTab);
    auto *splitter = new QSplitter(Qt::Vertical);
    m_assetTree = new QTreeWidget;
    m_assetTree->setColumnCount(6);
    m_assetTree->setHeaderLabels(
        {QStringLiteral("主机 / 端口"), QStringLiteral("服务"), QStringLiteral("产品"),
         QStringLiteral("版本"), QStringLiteral("风险"), QStringLiteral("Banner / 页面标题")});
    m_assetTree->setRootIsDecorated(true);
    m_assetTree->setAlternatingRowColors(true);
    m_assetTree->header()->setStretchLastSection(true);
    m_assetTree->setColumnWidth(0, 170);
    splitter->addWidget(m_assetTree);
    m_detailView = new QTextBrowser;
    m_detailView->setMinimumHeight(150);
    m_detailView->setOpenExternalLinks(true);
    splitter->addWidget(m_detailView);
    splitter->setStretchFactor(0, 3);
    splitter->setStretchFactor(1, 1);
    assetLayout->addWidget(splitter);
    tabs->addTab(assetTab, QStringLiteral("资产结果"));

    // 漏洞表
    auto *vulnTab = new QWidget;
    auto *vulnLayout = new QVBoxLayout(vulnTab);
    auto *filterRow = new QHBoxLayout;
    filterRow->addWidget(new QLabel(QStringLiteral("级别过滤:")));
    m_sevFilter = new QComboBox;
    m_sevFilter->addItems({QStringLiteral("全部"), QStringLiteral("严重"),
                           QStringLiteral("高危"), QStringLiteral("中危"),
                           QStringLiteral("低危"), QStringLiteral("信息")});
    filterRow->addWidget(m_sevFilter);
    filterRow->addStretch();
    vulnLayout->addLayout(filterRow);

    m_findTable = new QTableWidget;
    m_findTable->setColumnCount(5);
    m_findTable->setHorizontalHeaderLabels(
        {QStringLiteral("级别"), QStringLiteral("编号"), QStringLiteral("目标"),
         QStringLiteral("产品/版本"), QStringLiteral("漏洞名称")});
    m_findTable->setEditTriggers(QAbstractItemView::NoEditTriggers);
    m_findTable->setSelectionBehavior(QAbstractItemView::SelectRows);
    m_findTable->setAlternatingRowColors(true);
    m_findTable->horizontalHeader()->setStretchLastSection(true);
    m_findTable->setColumnWidth(0, 60);
    m_findTable->setColumnWidth(1, 150);
    m_findTable->setColumnWidth(2, 170);
    m_findTable->setColumnWidth(3, 200);
    vulnLayout->addWidget(m_findTable);
    tabs->addTab(vulnTab, QStringLiteral("漏洞列表"));

    // 日志
    m_logView = new QPlainTextEdit;
    m_logView->setReadOnly(true);
    tabs->addTab(m_logView, QStringLiteral("运行日志"));

    root->addWidget(tabs, 1);

    setCentralWidget(central);

    // ---------------- 菜单 ----------------
    auto *fileMenu = menuBar()->addMenu(QStringLiteral("文件(&F)"));
    fileMenu->addAction(QStringLiteral("导出 HTML 报告..."), this, &MainWindow::exportHtml);
    fileMenu->addAction(QStringLiteral("导出 CSV..."), this, &MainWindow::exportCsv);
    fileMenu->addSeparator();
    fileMenu->addAction(QStringLiteral("退出"), qApp, &QApplication::quit);
    auto *helpMenu = menuBar()->addMenu(QStringLiteral("帮助(&H)"));
    helpMenu->addAction(QStringLiteral("关于 / 使用声明"), this, &MainWindow::showAbout);

    // ---------------- 信号 ----------------
    connect(m_startBtn, &QPushButton::clicked, this, &MainWindow::startScan);
    connect(m_stopBtn, &QPushButton::clicked, this, &MainWindow::stopScan);
    connect(m_presetCombo, QOverload<int>::of(&QComboBox::currentIndexChanged),
            this, &MainWindow::onPresetChanged);
    connect(m_assetTree, &QTreeWidget::currentItemChanged,
            this, [this](QTreeWidgetItem *cur, QTreeWidgetItem *) {
        if (cur) onPortItemSelected();
    });
    connect(m_findTable, &QTableWidget::cellDoubleClicked,
            this, [this](int row, int) { onFindingActivated(row); });
    connect(m_sevFilter, QOverload<int>::of(&QComboBox::currentIndexChanged),
            this, [this](int) { applyFindingFilter(); });

    statusBar()->showMessage(
        QStringLiteral("规则库: %1 条 | SSL 支持: %2")
            .arg(m_db.ruleCount())
            .arg(QSslSocket::supportsSsl()
                     ? QSslSocket::sslLibraryVersionString()
                     : QStringLiteral("不可用(将跳过 HTTPS 探测)")));
}

void MainWindow::onPresetChanged(int index)
{
    m_customPortsEdit->setEnabled(index == 5);
}

QVector<quint16> MainWindow::currentPorts(QString *err) const
{
    switch (m_presetCombo->currentIndex()) {
    case 0: return ServiceDB::presetTopPorts();
    case 1: return ServiceDB::presetStandardPorts();
    case 2: return ServiceDB::presetWebPorts();
    case 3: return ServiceDB::presetDevicePorts();
    case 4: return ServiceDB::allPorts();
    default:
        return ServiceDB::parsePortSpec(m_customPortsEdit->text(), err);
    }
}

void MainWindow::resetResults()
{
    m_hosts.clear();
    m_hostNodes.clear();
    m_flatFindings.clear();
    m_assetTree->clear();
    m_findTable->setRowCount(0);
    m_detailView->clear();
    m_logView->clear();
    m_statLabel->clear();
    m_progress->setValue(0);
}

void MainWindow::startScan()
{
    QString targetText = m_targetsEdit->toPlainText().trimmed();
    if (targetText.isEmpty()) {
        QMessageBox::warning(this, QStringLiteral("提示"),
                             QStringLiteral("请输入扫描目标"));
        return;
    }
    QString err;
    const QVector<quint16> ports = currentPorts(&err);
    if (ports.isEmpty()) {
        QMessageBox::warning(this, QStringLiteral("提示"),
                             err.isEmpty() ? QStringLiteral("端口列表为空") : err);
        return;
    }

    if (m_presetCombo->currentIndex() == 4) {
        const auto ret = QMessageBox::question(
            this, QStringLiteral("确认全端口扫描"),
            QStringLiteral("全端口扫描每个主机将产生 65535 个连接任务，耗时较长且流量明显，确认继续？"));
        if (ret != QMessageBox::Yes) return;
    }

    resetResults();
    m_targetText = targetText;

    ScanOptions opts;
    opts.connectTimeoutMs = m_timeoutSpin->value();
    opts.readTimeoutMs = m_timeoutSpin->value() + 1000;
    opts.concurrency = m_concurrencySpin->value();
    opts.hostDiscovery = m_discoveryCheck->isChecked();

    m_engine = new ScanEngine(&m_db);
    m_worker = new QThread;
    m_engine->configure(targetText, ports, opts);
    m_engine->moveToThread(m_worker);

    connect(m_worker, &QThread::started, m_engine, &ScanEngine::start);
    connect(m_engine, &ScanEngine::log, this, &MainWindow::onLog, Qt::QueuedConnection);
    connect(m_engine, &ScanEngine::hostUp, this, &MainWindow::onHostUp, Qt::QueuedConnection);
    connect(m_engine, &ScanEngine::portFound, this, &MainWindow::onPortFound, Qt::QueuedConnection);
    connect(m_engine, &ScanEngine::progress, this, &MainWindow::onProgress, Qt::QueuedConnection);
    connect(m_engine, &ScanEngine::finished, this, &MainWindow::onFinished, Qt::QueuedConnection);
    connect(m_engine, &ScanEngine::finished, m_worker, &QThread::quit, Qt::QueuedConnection);
    connect(m_worker, &QThread::finished, m_engine, &QObject::deleteLater);
    connect(m_worker, &QThread::finished, this, [this]() {
        m_engine = nullptr;
    });

    setRunning(true);
    m_progress->setFormat(QStringLiteral("扫描中..."));
    m_worker->start();
}

void MainWindow::stopScan()
{
    if (m_engine) {
        m_engine->cancel();
        m_stopBtn->setEnabled(false);
        onLog(QStringLiteral("用户请求停止..."));
    }
}

void MainWindow::setRunning(bool running)
{
    m_startBtn->setEnabled(!running);
    m_stopBtn->setEnabled(running);
    m_targetsEdit->setReadOnly(running);
    m_presetCombo->setEnabled(!running);
    m_customPortsEdit->setEnabled(!running && m_presetCombo->currentIndex() == 5);
    m_timeoutSpin->setEnabled(!running);
    m_concurrencySpin->setEnabled(!running);
    m_discoveryCheck->setEnabled(!running);
}

void MainWindow::onHostUp(const QString &address, const QString &label)
{
    if (!m_hosts.contains(address)) {
        HostResult h;
        h.address = address;
        h.hostname = label;
        h.up = true;
        m_hosts.insert(address, h);

        auto *item = new QTreeWidgetItem(m_assetTree);
        item->setText(0, address);
        item->setText(1, QStringLiteral("up"));
        QFont bold = item->font(0);
        bold.setBold(true);
        item->setFont(0, bold);
        m_hostNodes.insert(address, item);
    } else {
        m_hosts[address].up = true;
        if (m_hostNodes.contains(address))
            m_hostNodes[address]->setText(1, QStringLiteral("up"));
    }
}

void MainWindow::onPortFound(const PortResult &r)
{
    onHostUp(r.host, r.host);

    HostResult &h = m_hosts[r.host];
    h.ports.append(r);

    QTreeWidgetItem *parent = m_hostNodes.value(r.host);

    // 端口数排序插入
    int row = 0;
    const int childCount = parent->childCount();
    while (row < childCount && parent->child(row)->text(0).toInt() < r.port)
        ++row;

    auto *item = new QTreeWidgetItem;
    item->setText(0, QString::number(r.port) + (r.tls ? QStringLiteral(" (TLS)") : QString()));
    item->setText(1, r.service.isEmpty() ? r.serviceHint : r.service);
    item->setText(2, r.product);
    item->setText(3, r.version);

    int worst = -1;
    for (const VulnFinding &f : r.findings)
        worst = qMax(worst, f.severity);
    if (worst >= 0) {
        item->setForeground(4, QColor(sevColor(worst)));
        item->setText(4, QStringLiteral("%1 (%2)")
                          .arg(severityText(worst)).arg(r.findings.size()));
    } else {
        item->setText(4, QStringLiteral("-"));
    }

    QString detail = r.banner;
    if (!r.httpTitle.isEmpty())
        detail += QStringLiteral(" | ") + r.httpTitle;
    item->setText(5, detail.left(220));
    parent->insertChild(row, item);
    parent->setExpanded(true);

    // 父节点风险计数
    int parentWorst = 0;
    int totalFindings = 0;
    for (const PortResult &p : h.ports) {
        for (const VulnFinding &f : p.findings) {
            parentWorst = qMax(parentWorst, f.severity);
            ++totalFindings;
        }
    }
    if (totalFindings > 0) {
        parent->setForeground(4, QColor(sevColor(parentWorst)));
        parent->setText(4, QStringLiteral("%1 条")
                            .arg(totalFindings));
    }

    for (const VulnFinding &f : r.findings)
        addFindingRow(r.host, r.port, f);
    applyFindingFilter();
}

void MainWindow::addFindingRow(const QString &host, quint16 port, const VulnFinding &f)
{
    FlatFinding ff;
    ff.host = host;
    ff.port = port;
    ff.f = f;
    m_flatFindings.append(ff);
    const int idx = m_flatFindings.size() - 1;

    const int row = m_findTable->rowCount();
    m_findTable->insertRow(row);
    auto *sevItem = new QTableWidgetItem(severityText(f.severity));
    sevItem->setData(Qt::UserRole, idx);
    sevItem->setForeground(QColor(sevColor(f.severity)));
    QFont bold = sevItem->font();
    bold.setBold(true);
    sevItem->setFont(bold);
    m_findTable->setItem(row, 0, sevItem);
    m_findTable->setItem(row, 1, new QTableWidgetItem(f.ruleId));
    m_findTable->setItem(row, 2, new QTableWidgetItem(
                             QStringLiteral("%1:%2").arg(host).arg(port)));
    m_findTable->setItem(row, 3, new QTableWidgetItem(
                             f.product + (f.version.isEmpty() ? QString() : QStringLiteral(" ") + f.version)));
    m_findTable->setItem(row, 4, new QTableWidgetItem(f.title));
}

void MainWindow::applyFindingFilter()
{
    const int wanted = m_sevFilter->currentIndex() - 1; // 0 = 全部
    for (int i = 0; i < m_findTable->rowCount(); ++i) {
        const int idx = m_findTable->item(i, 0)->data(Qt::UserRole).toInt();
        const int sev = m_flatFindings.value(idx).f.severity;
        m_findTable->setRowHidden(i, wanted >= 0 && sev != wanted);
    }
}

void MainWindow::onPortItemSelected()
{
    QTreeWidgetItem *cur = m_assetTree->currentItem();
    if (!cur) return;
    QTreeWidgetItem *par = cur->parent();
    if (!par) return; // 主机行
    const QString host = par->text(0);
    const quint16 port = static_cast<quint16>(cur->text(0).section(QLatin1Char(' '), 0, 0).toUShort());

    const HostResult &h = m_hosts.value(host);
    const PortResult *pr = nullptr;
    for (const PortResult &p : h.ports)
        if (p.port == port) { pr = &p; break; }
    if (!pr) return;

    QString html = QStringLiteral(
        "<style>body{font-family:'Microsoft YaHei';font-size:13px;}"
        "h3{margin:6px 0;}table{border-collapse:collapse;}td{padding:2px 10px 2px 0;vertical-align:top;}"
        ".sev{color:%1;font-weight:bold;}.code{font-family:Consolas;background:#f4f4f4;padding:6px;}</style>")
        .arg(sevColor(SevCritical));

    html += QStringLiteral("<h3>%1 : %2</h3><table>")
                .arg(host).arg(port);
    html += QStringLiteral("<tr><td>服务</td><td>%1</td></tr>")
                .arg(pr->service.isEmpty() ? pr->serviceHint : pr->service.toHtmlEscaped());
    if (!pr->product.isEmpty())
        html += QStringLiteral("<tr><td>产品</td><td>%1 %2</td></tr>")
                    .arg(pr->product.toHtmlEscaped(), pr->version.toHtmlEscaped());
    html += QStringLiteral("<tr><td>延迟</td><td>%1 ms</td></tr>").arg(pr->latencyMs);
    if (pr->tls)
        html += QStringLiteral("<tr><td>传输</td><td>TLS 加密</td></tr>");
    if (pr->unauth)
        html += QStringLiteral("<tr><td colspan=2 style='color:#b71c1c;font-weight:bold;'>"
                               "确认存在未授权访问</td></tr>");
    html += QStringLiteral("</table>");

    if (!pr->banner.isEmpty())
        html += QStringLiteral("<p><b>Banner / 指纹:</b></p><div class='code'>%1</div>")
                    .arg(pr->banner.left(2000).toHtmlEscaped());
    if (pr->httpStatus > 0) {
        html += QStringLiteral("<p><b>HTTP:</b> 状态 %1").arg(pr->httpStatus);
        if (!pr->httpTitle.isEmpty())
            html += QStringLiteral(" | 标题: %1").arg(pr->httpTitle.toHtmlEscaped());
        if (!pr->httpServer.isEmpty())
            html += QStringLiteral(" | Server: %1").arg(pr->httpServer.toHtmlEscaped());
        html += QStringLiteral("</p>");
        for (auto it = pr->httpHeaders.constBegin(); it != pr->httpHeaders.constEnd(); ++it)
            html += QStringLiteral("<div class='code'>%1: %2</div>")
                        .arg(it.key().toHtmlEscaped(), it.value().toHtmlEscaped());
    }

    if (!pr->findings.isEmpty()) {
        html += QStringLiteral("<h3>命中漏洞 (%1)</h3>").arg(pr->findings.size());
        for (const VulnFinding &f : pr->findings) {
            html += QStringLiteral(
                "<p><span class='sev' style='color:%1'>[%2] %3</span><br>"
                "<b>%4</b><br>%5<br><span style='color:#2e7d32;'><b>修复:</b> %6</span>")
                .arg(sevColor(f.severity), severityText(f.severity),
                     f.ruleId.toHtmlEscaped(), f.title.toHtmlEscaped(),
                     f.description.toHtmlEscaped(), f.solution.toHtmlEscaped());
            if (!f.reference.isEmpty())
                html += QStringLiteral("<br><a href='%1'>%1</a>").arg(f.reference.toHtmlEscaped());
            html += QStringLiteral("</p>");
        }
    }
    m_detailView->setHtml(html);
}

void MainWindow::onFindingActivated(int row)
{
    const int idx = m_findTable->item(row, 0)->data(Qt::UserRole).toInt();
    const FlatFinding &ff = m_flatFindings.value(idx);
    const VulnFinding &f = ff.f;
    QString text = QStringLiteral("级别: %1\n编号: %2\n目标: %3:%4\n产品: %5 %6\n\n%7\n\n修复建议:\n%8")
                       .arg(severityText(f.severity), f.ruleId, ff.host)
                       .arg(ff.port)
                       .arg(f.product, f.version, f.description, f.solution);
    if (!f.reference.isEmpty())
        text += QStringLiteral("\n\n参考: ") + f.reference;
    QMessageBox::information(this, f.title, text);
}

void MainWindow::onProgress(int done, int total)
{
    if (total <= 0) return;
    m_progress->setRange(0, total);
    m_progress->setValue(done);
    m_progress->setFormat(QStringLiteral("%v / %m (%p%)"));
}

void MainWindow::onLog(const QString &msg)
{
    const QString line = QStringLiteral("[%1] %2")
                             .arg(QDateTime::currentDateTime().toString(QStringLiteral("HH:mm:ss")), msg);
    m_logView->appendPlainText(line);
}

void MainWindow::onFinished(const ScanSummary &s)
{
    setRunning(false);
    m_stopBtn->setEnabled(false);
    m_progress->setRange(0, 1);
    m_progress->setValue(1);
    m_progress->setFormat(QStringLiteral("扫描完成"));
    m_statLabel->setText(
        QStringLiteral("主机 %1/%2 | 开放端口 %3 | 严重 %4 高危 %5 中危 %6 低危 %7 信息 %8 | %9s")
            .arg(s.hostsUp).arg(s.hostsTotal).arg(s.openPorts)
            .arg(s.critical).arg(s.high).arg(s.medium).arg(s.low).arg(s.info)
            .arg(s.elapsedMs / 1000.0, 0, 'f', 1));
}

void MainWindow::exportHtml()
{
    if (m_hosts.isEmpty()) {
        QMessageBox::information(this, QStringLiteral("提示"),
                                 QStringLiteral("暂无可导出的扫描结果"));
        return;
    }
    const QString name = QStringLiteral("vuln_report_%1.html")
                             .arg(QDateTime::currentDateTime().toString(QStringLiteral("yyyyMMdd_HHmmss")));
    const QString path = QFileDialog::getSaveFileName(
        this, QStringLiteral("导出 HTML 报告"), name, QStringLiteral("HTML (*.html)"));
    if (path.isEmpty()) return;
    QString err;
    ScanSummary sum;
    for (const HostResult &h : m_hosts) {
        ++sum.hostsUp;
        for (const PortResult &p : h.ports) {
            ++sum.openPorts;
            for (const VulnFinding &f : p.findings) {
                switch (f.severity) {
                case SevCritical: ++sum.critical; break;
                case SevHigh: ++sum.high; break;
                case SevMedium: ++sum.medium; break;
                case SevLow: ++sum.low; break;
                default: ++sum.info; break;
                }
            }
        }
    }
    if (!ReportExport::exportHtml(path, m_hosts, sum, m_targetText, &err))
        QMessageBox::warning(this, QStringLiteral("导出失败"), err);
    else
        statusBar()->showMessage(QStringLiteral("已导出: ") + path, 5000);
}

void MainWindow::exportCsv()
{
    if (m_hosts.isEmpty()) {
        QMessageBox::information(this, QStringLiteral("提示"),
                                 QStringLiteral("暂无可导出的扫描结果"));
        return;
    }
    const QString name = QStringLiteral("vuln_report_%1.csv")
                             .arg(QDateTime::currentDateTime().toString(QStringLiteral("yyyyMMdd_HHmmss")));
    const QString path = QFileDialog::getSaveFileName(
        this, QStringLiteral("导出 CSV"), name, QStringLiteral("CSV (*.csv)"));
    if (path.isEmpty()) return;
    QString err;
    if (!ReportExport::exportCsv(path, m_hosts, &err))
        QMessageBox::warning(this, QStringLiteral("导出失败"), err);
    else
        statusBar()->showMessage(QStringLiteral("已导出: ") + path, 5000);
}

void MainWindow::showAbout()
{
    QMessageBox::about(this, QStringLiteral("关于"),
        QStringLiteral("<h3>服务器与网络设备漏洞扫描工具 v1.0</h3>"
                       "<p>基于 Qt %1 开发，仅依赖 Qt Network。</p>"
                       "<p>能力：IP/CIDR/范围/域名目标解析、TCP 全连接端口扫描、"
                       "Banner 服务识别（SSH/FTP/SMTP/MySQL/Redis/Memcached/ZooKeeper/rsync 等）、"
                       "HTTP/HTTPS 指纹与标题识别、安防设备指纹、CVE 规则库版本匹配、"
                       "未授权访问检查、HTML/CSV 报告导出。</p>"
                       "<p style='color:#b71c1c;'><b>仅限对已获书面授权的目标使用，"
                       "禁止未授权扫描，使用者自行承担法律责任。</b></p>")
            .arg(QT_VERSION_STR));
}
