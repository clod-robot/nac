#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QMainWindow>
#include <QMap>
#include "model/scantypes.h"
#include "vuln/vulndb.h"

class QPlainTextEdit;
class QLineEdit;
class QComboBox;
class QSpinBox;
class QCheckBox;
class QPushButton;
class QProgressBar;
class QLabel;
class QTreeWidget;
class QTreeWidgetItem;
class QTableWidget;
class QTextBrowser;
class QThread;
class ScanEngine;

class MainWindow : public QMainWindow
{
    Q_OBJECT
public:
    explicit MainWindow(QWidget *parent = nullptr);

private slots:
    void startScan();
    void stopScan();
    void onPortFound(const PortResult &r);
    void onHostUp(const QString &address, const QString &label);
    void onProgress(int done, int total);
    void onLog(const QString &msg);
    void onFinished(const ScanSummary &s);
    void exportHtml();
    void exportCsv();
    void showAbout();
    void onPortItemSelected();
    void onFindingActivated(int row);
    void applyFindingFilter();
    void onPresetChanged(int index);

private:
    void buildUi();
    QVector<quint16> currentPorts(QString *err) const;
    void addFindingRow(const QString &host, quint16 port, const VulnFinding &f);
    void resetResults();
    void setRunning(bool running);

    // 控件：目标配置
    QPlainTextEdit *m_targetsEdit;
    QComboBox *m_presetCombo;
    QLineEdit *m_customPortsEdit;
    QSpinBox *m_timeoutSpin;
    QSpinBox *m_concurrencySpin;
    QCheckBox *m_discoveryCheck;
    QPushButton *m_startBtn;
    QPushButton *m_stopBtn;
    QProgressBar *m_progress;
    QLabel *m_statLabel;

    // 结果
    QTreeWidget *m_assetTree;
    QTableWidget *m_findTable;
    QTextBrowser *m_detailView;
    QPlainTextEdit *m_logView;
    QComboBox *m_sevFilter;

    // 数据
    QMap<QString, HostResult> m_hosts;
    QMap<QString, QTreeWidgetItem *> m_hostNodes;
    struct FlatFinding { QString host; quint16 port; VulnFinding f; };
    QVector<FlatFinding> m_flatFindings;
    QString m_targetText;

    // 引擎
    VulnDb m_db;
    ScanEngine *m_engine = nullptr;
    QThread *m_worker = nullptr;
};

#endif // MAINWINDOW_H
