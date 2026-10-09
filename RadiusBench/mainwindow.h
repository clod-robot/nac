#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QMainWindow>
#include <QString>
#include <QList>

// 测试结果
struct BenchResult {
    int concurrency;
    double throughput;
    int success;
    int fail;
    int error;
    double successRate;
    double avgMs, p50Ms, p95Ms, p99Ms, maxMs;
};

class QLineEdit;
class QComboBox;
class QTextEdit;
class QPushButton;
class QThread;

class MainWindow : public QMainWindow {
    Q_OBJECT
public:
    explicit MainWindow(QWidget *parent = nullptr);
    ~MainWindow();

private slots:
    void onStartTest();
    void onTestProgress(const QString &msg);
    void onTestFinished();
    void onSmokeTest();

private:
    // 配置区
    QLineEdit *serverEdit;
    QLineEdit *portEdit;
    QLineEdit *secretEdit;
    QComboBox *modeCombo;       // PAP / EAP-MD5
    QLineEdit *concurrencyEdit; // "50,100,200"
    QLineEdit *warmupEdit;
    QLineEdit *durationEdit;
    QLineEdit *usernameEdit;
    QLineEdit *passwordEdit;

    // 操作区
    QPushButton *startBtn;
    QPushButton *smokeBtn;
    QTextEdit *outputEdit;

    // 测试线程
    QThread *workerThread;
    volatile bool running;

    // 运行分级压测
    void runSweep(const QString &server, int port, const QString &secret,
                  const QString &mode, const QList<int> &levels,
                  int warmup, int duration,
                  const QString &user, const QString &pwd);
};

#endif // MAINWINDOW_H
