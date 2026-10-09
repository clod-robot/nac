#include "mainwindow.h"

#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFormLayout>
#include <functional>
#include <QApplication>
#include <QLabel>
#include <QLineEdit>
#include <QComboBox>
#include <QPushButton>
#include <QTextEdit>
#include <QThread>
#include <QDateTime>
#include <QHostAddress>
#include <QUdpSocket>
#include <QCryptographicHash>
#include <QTimer>
#include <QElapsedTimer>
#include <QVector>
#include <QSet>
#include <algorithm>
#include <limits>

// ==================== RADIUS 协议常量 ====================
static const quint8 CODE_ACCESS_REQUEST = 1;
static const quint8 CODE_ACCESS_ACCEPT = 2;
static const quint8 CODE_ACCESS_REJECT = 3;
static const quint8 CODE_ACCESS_CHALLENGE = 11;
static const quint8 ATTR_USER_NAME = 1;
static const quint8 ATTR_USER_PASSWORD = 2;
static const quint8 ATTR_NAS_IP = 4;
static const quint8 ATTR_CALLING_STATION = 31;
static const quint8 ATTR_NAS_IDENT = 32;
static const quint8 ATTR_EAP_MSG = 79;
static const quint8 ATTR_MSG_AUTH = 80;

// ==================== RADIUS 包构造/解析 ====================
static QByteArray makeAttr(quint8 type, const QByteArray &val) {
    QByteArray r;
    r.append((char)type);
    r.append((char)(2 + val.size()));
    r.append(val);
    return r;
}

static QByteArray encodeRadius(quint8 code, quint8 reqId, const QByteArray &auth, const QByteArray &attrs) {
    QByteArray pkt;
    quint16 total = 20 + attrs.size();
    pkt.append((char)code);
    pkt.append((char)reqId);
    pkt.append((char)(total >> 8));
    pkt.append((char)(total & 0xFF));
    pkt.append(auth);
    pkt.append(attrs);
    return pkt;
}

static QMap<int, QByteArray> parseAttrs(const QByteArray &data) {
    QMap<int, QByteArray> out;
    int i = 0;
    while (i + 2 <= data.size()) {
        quint8 t = (quint8)data[i];
        quint8 ln = (quint8)data[i+1];
        if (ln < 2 || i + ln > data.size()) break;
        out[t] += data.mid(i + 2, ln - 2);
        i += ln;
    }
    return out;
}

// PAP 密码加密
static QByteArray papPassword(const QString &password, const QByteArray &ra) {
    QByteArray pw = password.toUtf8();
    int padLen = ((pw.size() + 15) / 16) * 16;
    if (padLen == 0) padLen = 16;
    pw.resize(padLen);
    for (int i = pw.size(); i < padLen; i++) pw.append('\0');
    QByteArray res, prev = ra;
    for (int off = 0; off < pw.size(); off += 16) {
        QByteArray block = pw.mid(off, 16);
        QCryptographicHash md5(QCryptographicHash::Md5);
        md5.addData(ra);
        md5.addData(prev);
        QByteArray h = md5.result();
        QByteArray enc;
        for (int j = 0; j < 16; j++) enc.append(block[j] ^ h[j]);
        res += enc;
        prev = enc;
    }
    return res;
}

// EAP Response/Identity
static QByteArray eapIdentity(quint8 eapId, const QString &username) {
    QByteArray u = username.toUtf8();
    QByteArray eap;
    eap.append((char)2);          // Code: Response
    eap.append((char)eapId);     // Id
    quint16 len = 5 + u.size();
    eap.append((char)(len >> 8));
    eap.append((char)(len & 0xFF));
    eap.append((char)1);          // Type: Identity
    eap.append(u);
    return eap;
}

// EAP Response/MD5-Challenge
static QByteArray eapMd5Response(quint8 eapId, const QString &password, const QByteArray &challenge) {
    QCryptographicHash md5(QCryptographicHash::Md5);
    md5.addData(QByteArray(1, eapId));
    md5.addData(password.toUtf8());
    md5.addData(challenge);
    QByteArray h = md5.result();
    QByteArray data;
    data.append((char)16);    // value-size
    data.append(h);
    QByteArray eap;
    eap.append((char)2);          // Code: Response
    eap.append((char)eapId);
    quint16 len = 5 + data.size();
    eap.append((char)(len >> 8));
    eap.append((char)(len & 0xFF));
    eap.append((char)4);          // Type: MD5-Challenge
    eap.append(data);
    return eap;
}

// ==================== 压测工作器（非 QObject，无 signals/slots） ====================
class BenchWorker {
public:
    QString server;
    int port;
    QString secret;
    QString mode;
    QList<int> levels;
    int warmup;
    int duration;
    QString username;
    QString password;

    void doWork() {
        for (int c : levels) {
            BenchResult r;
            r.concurrency = c;
            runStage(c, r);
            // emit progress(formatResult(r));
        }
    }

private:
    void runStage(int concurrency, BenchResult &result) {
        QUdpSocket sock;
        sock.bind(QHostAddress(QHostAddress::Any), quint16(0));
        sock.connectToHost(QHostAddress(server), port);
        if (!sock.waitForConnected(3000)) {
            return;
        }

        // 同步交换函数
        auto exchange = [&](const QByteArray &pkt, int timeoutMs) -> QByteArray {
            sock.write(pkt);
            if (sock.hasPendingDatagrams() || sock.waitForReadyRead(timeoutMs)) {
                QByteArray buf;
                buf.resize(sock.pendingDatagramSize());
                sock.readDatagram(buf.data(), buf.size());
                return buf;
            }
            return QByteArray();
        };

        // MAC 生成
        auto macFor = [](int i) -> QString {
            return QString("02-00-%1-%2-%3-00")
                .arg((i >> 16) & 0xFF, 2, 16, QChar('0'))
                .arg((i >> 8) & 0xFF, 2, 16, QChar('0'))
                .arg(i & 0xFF, 2, 16, QChar('0'));
        };

        QByteArray secretBytes = secret.toUtf8();

        // 单次认证
        auto doAuth = [&](int workerIdx) -> int {
            QString station = macFor(workerIdx + 1);
            QByteArray ra(16, '\0');
            for (int i = 0; i < 16; i++) ra[i] = (char)(rand() & 0xFF);

            if (mode == "pap") {
                QByteArray body = makeAttr(ATTR_USER_NAME, username.toUtf8()) +
                    makeAttr(ATTR_USER_PASSWORD, papPassword(password, ra)) +
                    makeAttr(ATTR_NAS_IDENT, "nac-bench") +
                    makeAttr(ATTR_NAS_IP, QByteArray(4, '\0')) +
                    makeAttr(ATTR_CALLING_STATION, station.toUtf8());
                QByteArray pkt = encodeRadius(CODE_ACCESS_REQUEST, workerIdx & 0xFF, ra, body);
                QByteArray resp = exchange(pkt, 3000);
                if (resp.size() >= 1) return resp[0];
                return -1;
            } else {
                // EAP-MD5
                QByteArray eap1 = eapIdentity(1, username);
                QByteArray body = makeAttr(ATTR_USER_NAME, username.toUtf8()) +
                    makeAttr(ATTR_NAS_IDENT, "nac-bench") +
                    makeAttr(ATTR_NAS_IP, QByteArray(4, '\0')) +
                    makeAttr(ATTR_CALLING_STATION, station.toUtf8()) +
                    makeAttr(ATTR_MSG_AUTH, QByteArray(16, '\0')) +
                    makeAttr(ATTR_EAP_MSG, eap1);
                QByteArray pkt = encodeRadius(CODE_ACCESS_REQUEST, workerIdx & 0xFF, ra, body);
                QByteArray resp = exchange(pkt, 3000);
                if (resp.size() < 1) return -1;
                if ((quint8)resp[0] != CODE_ACCESS_CHALLENGE) return resp[0];

                // 解析 EAP Challenge
                QMap<int, QByteArray> attrs = parseAttrs(resp.mid(20));
                QByteArray eapData = attrs.value(ATTR_EAP_MSG);
                if (eapData.size() < 5) return -1;
                quint8 srvEapId = (quint8)eapData[1];
                // EAP Type 4 (MD5-Challenge): type(1) + value-size(1) + value(n)
                QByteArray challenge;
                if (eapData.size() >= 6) {
                    int valSize = (quint8)eapData[5];
                    challenge = eapData.mid(6, valSize);
                }
                QByteArray eap2 = eapMd5Response(srvEapId, password, challenge);
                QByteArray body2 = makeAttr(ATTR_USER_NAME, username.toUtf8()) +
                    makeAttr(ATTR_NAS_IDENT, "nac-bench") +
                    makeAttr(ATTR_NAS_IP, QByteArray(4, '\0')) +
                    makeAttr(ATTR_CALLING_STATION, station.toUtf8()) +
                    makeAttr(ATTR_MSG_AUTH, QByteArray(16, '\0')) +
                    makeAttr(ATTR_EAP_MSG, eap2);
                QByteArray pkt2 = encodeRadius(CODE_ACCESS_REQUEST, (workerIdx + 1) & 0xFF, ra, body2);
                QByteArray resp2 = exchange(pkt2, 3000);
                if (resp2.size() >= 1) return resp2[0];
                return -1;
            }
        };

        // 预热（不统计）
        QElapsedTimer wt;
        wt.start();
        while (wt.elapsed() < warmup * 1000) {
            for (int i = 0; i < concurrency; i++) doAuth(i);
        }

        // 正式测量
        QElapsedTimer timer;
        timer.start();

        QVector<double> latencies;
        latencies.reserve(concurrency * duration * 2);
        int success = 0, fail = 0, errors = 0;

        while (timer.elapsed() < duration * 1000) {
            for (int i = 0; i < concurrency; i++) {
                qint64 t0 = QDateTime::currentMSecsSinceEpoch();
                int code = doAuth(i);
                qint64 dt = QDateTime::currentMSecsSinceEpoch() - t0;
                latencies.append(dt);
                if (code == CODE_ACCESS_ACCEPT) success++;
                else if (code == CODE_ACCESS_REJECT) fail++;
                else errors++;
            }
            // 防止事件循环卡死
            QCoreApplication::processEvents();
        }

        double elapsed = timer.elapsed() / 1000.0;
        int total = success + fail + errors;

        result.throughput = elapsed > 0 ? total / elapsed : 0;
        result.success = success;
        result.fail = fail;
        result.error = errors;
        result.successRate = total > 0 ? success * 100.0 / total : 0;

        if (!latencies.isEmpty()) {
            std::sort(latencies.begin(), latencies.end());
            double sum = 0;
            for (double v : latencies) sum += v;
            result.avgMs = sum / latencies.size();
            int n = latencies.size();
            result.p50Ms = latencies[n * 50 / 100];
            result.p95Ms = latencies[std::min(n - 1, n * 95 / 100)];
            result.p99Ms = latencies[std::min(n - 1, n * 99 / 100)];
            result.maxMs = latencies.last();
        }
    }

    QString formatResult(const BenchResult &r) {
        return QString("[%1] C=%2  吞吐=%3 auth/s  成功=%4  失败=%5  错误=%6  成功率=%7%  "
                       "avg=%8ms  p50=%9ms  p95=%10ms  p99=%11ms  max=%12ms")
            .arg(r.concurrency)
            .arg(r.concurrency)
            .arg(r.throughput, 0, 'f', 1)
            .arg(r.success)
            .arg(r.fail)
            .arg(r.error)
            .arg(r.successRate, 0, 'f', 2)
            .arg(r.avgMs, 0, 'f', 0)
            .arg(r.p50Ms, 0, 'f', 0)
            .arg(r.p95Ms, 0, 'f', 0)
            .arg(r.p99Ms, 0, 'f', 0)
            .arg(r.maxMs, 0, 'f', 0);
    }
};

// ==================== MainWindow ====================

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent), workerThread(nullptr), running(false)
{
    setWindowTitle("NAC RADIUS 压力测试工具");
    resize(850, 600);

    QWidget *central = new QWidget;
    QVBoxLayout *mainLayout = new QVBoxLayout(central);

    // 配置区
    QFormLayout *form = new QFormLayout;
    serverEdit = new QLineEdit("192.168.80.36");
    portEdit = new QLineEdit("1812");
    secretEdit = new QLineEdit("testing123");
    modeCombo = new QComboBox;
    modeCombo->addItems(QStringList() << "eap-md5" << "pap");
    concurrencyEdit = new QLineEdit("50,100,200");
    warmupEdit = new QLineEdit("8");
    durationEdit = new QLineEdit("30");
    usernameEdit = new QLineEdit("wangfang");
    passwordEdit = new QLineEdit("Wf$secure99");
    passwordEdit->setEchoMode(QLineEdit::Password);

    form->addRow("服务器地址:", serverEdit);
    form->addRow("RADIUS 端口:", portEdit);
    form->addRow("共享密钥:", secretEdit);
    form->addRow("认证模式:", modeCombo);
    form->addRow("并发级别(逗号分隔):", concurrencyEdit);
    form->addRow("预热秒数:", warmupEdit);
    form->addRow("每级测量秒数:", durationEdit);
    form->addRow("用户名:", usernameEdit);
    form->addRow("密码:", passwordEdit);

    mainLayout->addLayout(form);

    // 按钮区
    QHBoxLayout *btnLayout = new QHBoxLayout;
    startBtn = new QPushButton("开始测试");
    startBtn->setMinimumHeight(40);
    smokeBtn = new QPushButton("冒烟测试");
    smokeBtn->setMinimumHeight(40);
    btnLayout->addWidget(startBtn);
    btnLayout->addWidget(smokeBtn);
    mainLayout->addLayout(btnLayout);

    // 输出区
    outputEdit = new QTextEdit;
    outputEdit->setFont(QFont("Consolas", 10));
    outputEdit->setStyleSheet("QTextEdit { background-color: #1e1e1e; color: #d4d4d4; }");
    mainLayout->addWidget(outputEdit, 1);

    setCentralWidget(central);

    connect(startBtn, &QPushButton::clicked, this, &MainWindow::onStartTest);
    connect(smokeBtn, &QPushButton::clicked, this, &MainWindow::onSmokeTest);
}

MainWindow::~MainWindow() {
    if (workerThread) {
        running = false;
        workerThread->wait(3000);
        delete workerThread;
    }
}

void MainWindow::onSmokeTest() {
    QString server = serverEdit->text().trimmed();
    int port = portEdit->text().toInt();
    QString secret = secretEdit->text();
    QString mode = modeCombo->currentText();
    QString user = usernameEdit->text();
    QString pwd = passwordEdit->text();

    outputEdit->append(QString("=== 冒烟测试 %1:%2 (%3) ===").arg(server).arg(port).arg(mode));

    QUdpSocket sock;
    sock.bind(QHostAddress(QHostAddress::Any), quint16(0));
    sock.connectToHost(QHostAddress(server), port);
    if (!sock.waitForConnected(3000)) {
        outputEdit->append("连接失败: " + sock.errorString());
        return;
    }

    auto exchange = [&](const QByteArray &pkt, int timeoutMs) -> QByteArray {
        sock.write(pkt);
        if (sock.waitForReadyRead(timeoutMs)) {
            QByteArray buf;
            buf.resize(sock.pendingDatagramSize());
            sock.readDatagram(buf.data(), buf.size());
            return buf;
        }
        return QByteArray();
    };

    auto macStr = []() -> QString {
        return QString("02-00-%1-00-00-00").arg(rand() & 0xFF, 2, 16, QChar('0'));
    };

    // PAP 正确
    {
        QByteArray ra(16, '\0');
        for (int i = 0; i < 16; i++) ra[i] = (char)(rand() & 0xFF);
        QString station = macStr();
        QByteArray body = makeAttr(ATTR_USER_NAME, user.toUtf8()) +
            makeAttr(ATTR_USER_PASSWORD, papPassword(pwd, ra)) +
            makeAttr(ATTR_NAS_IDENT, "nac-bench") +
            makeAttr(ATTR_NAS_IP, QByteArray(4, '\0')) +
            makeAttr(ATTR_CALLING_STATION, station.toUtf8());
        QByteArray pkt = encodeRadius(CODE_ACCESS_REQUEST, 1, ra, body);
        QByteArray resp = exchange(pkt, 3000);
        QString label = resp.size() >= 1 ?
            ((quint8)resp[0] == CODE_ACCESS_ACCEPT ? "Access-Accept" :
             (quint8)resp[0] == CODE_ACCESS_REJECT ? "Access-Reject" : "Other") : "Timeout";
        outputEdit->append(QString("smoke pap        -> %1").arg(label));
    }
    // EAP-MD5
    {
        QByteArray ra(16, '\0');
        for (int i = 0; i < 16; i++) ra[i] = (char)(rand() & 0xFF);
        QString station = macStr();
        QByteArray eap1 = eapIdentity(1, user);
        QByteArray body = makeAttr(ATTR_USER_NAME, user.toUtf8()) +
            makeAttr(ATTR_NAS_IDENT, "nac-bench") +
            makeAttr(ATTR_NAS_IP, QByteArray(4, '\0')) +
            makeAttr(ATTR_CALLING_STATION, station.toUtf8()) +
            makeAttr(ATTR_MSG_AUTH, QByteArray(16, '\0')) +
            makeAttr(ATTR_EAP_MSG, eap1);
        QByteArray pkt = encodeRadius(CODE_ACCESS_REQUEST, 2, ra, body);
        QByteArray resp = exchange(pkt, 3000);
        if (resp.size() >= 1 && (quint8)resp[0] == CODE_ACCESS_CHALLENGE) {
            QMap<int, QByteArray> attrs = parseAttrs(resp.mid(20));
            QByteArray eapData = attrs.value(ATTR_EAP_MSG);
            quint8 srvEapId = (quint8)eapData[1];
            QByteArray challenge;
            if (eapData.size() >= 6) {
                int valSize = (quint8)eapData[5];
                challenge = eapData.mid(6, valSize);
            }
            QByteArray eap2 = eapMd5Response(srvEapId, pwd, challenge);
            QByteArray body2 = makeAttr(ATTR_USER_NAME, user.toUtf8()) +
                makeAttr(ATTR_NAS_IDENT, "nac-bench") +
                makeAttr(ATTR_NAS_IP, QByteArray(4, '\0')) +
                makeAttr(ATTR_CALLING_STATION, station.toUtf8()) +
                makeAttr(ATTR_MSG_AUTH, QByteArray(16, '\0')) +
                makeAttr(ATTR_EAP_MSG, eap2);
            QByteArray pkt2 = encodeRadius(CODE_ACCESS_REQUEST, 3, ra, body2);
            QByteArray resp2 = exchange(pkt2, 3000);
            QString label = resp2.size() >= 1 ?
                ((quint8)resp2[0] == CODE_ACCESS_ACCEPT ? "Access-Accept" :
                 (quint8)resp2[0] == CODE_ACCESS_REJECT ? "Access-Reject" : "Other") : "Timeout";
            outputEdit->append(QString("smoke eap-md5   -> %1").arg(label));
        } else {
            QString label = resp.size() >= 1 ? "Unexpected" : "Timeout";
            outputEdit->append(QString("smoke eap-md5   -> %1").arg(label));
        }
    }
    // PAP 错误密码
    {
        QByteArray ra(16, '\0');
        for (int i = 0; i < 16; i++) ra[i] = (char)(rand() & 0xFF);
        QString station = macStr();
        QByteArray body = makeAttr(ATTR_USER_NAME, user.toUtf8()) +
            makeAttr(ATTR_USER_PASSWORD, papPassword("wrong-pass", ra)) +
            makeAttr(ATTR_NAS_IDENT, "nac-bench") +
            makeAttr(ATTR_NAS_IP, QByteArray(4, '\0')) +
            makeAttr(ATTR_CALLING_STATION, station.toUtf8());
        QByteArray pkt = encodeRadius(CODE_ACCESS_REQUEST, 4, ra, body);
        QByteArray resp = exchange(pkt, 3000);
        QString label = resp.size() >= 1 ?
            ((quint8)resp[0] == CODE_ACCESS_ACCEPT ? "Access-Accept" :
             (quint8)resp[0] == CODE_ACCESS_REJECT ? "Access-Reject" : "Other") : "Timeout";
        outputEdit->append(QString("smoke pap-wrong -> %1").arg(label));
    }
    outputEdit->append("=== 冒烟测试完成 ===\n");
}

void MainWindow::onStartTest() {
    if (running) return;
    running = true;
    startBtn->setEnabled(false);
    smokeBtn->setEnabled(false);
    outputEdit->clear();

    QString server = serverEdit->text().trimmed();
    int port = portEdit->text().toInt();
    QString secret = secretEdit->text();
    QString mode = modeCombo->currentText();
    int warmup = warmupEdit->text().toInt();
    int duration = durationEdit->text().toInt();
    QString user = usernameEdit->text();
    QString pwd = passwordEdit->text();

    // 解析并发级别
    QStringList parts = concurrencyEdit->text().split(',');
    QList<int> levels;
    for (const QString &p : parts) {
        bool ok;
        int c = p.trimmed().toInt(&ok);
        if (ok && c > 0) levels.append(c);
    }
    if (levels.isEmpty()) {
        outputEdit->append("并发级别无效");
        running = false;
        startBtn->setEnabled(true);
        smokeBtn->setEnabled(true);
        return;
    }

    outputEdit->append(QString("=== 开始 %1 压测: %2:%3, 级别=%4, 预热=%5s, 每级=%6s ===")
        .arg(mode.toUpper()).arg(server).arg(port).arg(concurrencyEdit->text())
        .arg(warmup).arg(duration));
    outputEdit->append(QString("用户: %1\n").arg(user));

    // 在后台线程运行
    // 直接在主线程用 processEvents 跑（避免 QThread 复杂度）
    QUdpSocket sock;
    sock.bind(QHostAddress(QHostAddress::Any), quint16(0));
    sock.connectToHost(QHostAddress(server), port);
    if (!sock.waitForConnected(3000)) {
        outputEdit->append("连接服务器失败: " + sock.errorString());
        running = false;
        startBtn->setEnabled(true);
        smokeBtn->setEnabled(true);
        return;
    }

    QByteArray secretBytes = secret.toUtf8();
    auto exchange = [&](const QByteArray &pkt, int timeoutMs) -> QByteArray {
        sock.write(pkt);
        if (sock.waitForReadyRead(timeoutMs)) {
            // 清空缓冲
            QByteArray buf;
            buf.resize(sock.pendingDatagramSize());
            sock.readDatagram(buf.data(), buf.size());
            return buf;
        }
        return QByteArray();
    };

    auto macFor = [](int i) -> QString {
        return QString("02-00-%1-%2-%3-00")
            .arg((i >> 16) & 0xFF, 2, 16, QChar('0'))
            .arg((i >> 8) & 0xFF, 2, 16, QChar('0'))
            .arg(i & 0xFF, 2, 16, QChar('0'));
    };

    // doAuth lambda
    std::function<int(int)> doAuth = [&](int workerIdx) -> int {
        QString station = macFor(workerIdx + 1);
        QByteArray ra(16, '\0');
        for (int i = 0; i < 16; i++) ra[i] = (char)(rand() & 0xFF);

        if (mode == "pap") {
            QByteArray body = makeAttr(ATTR_USER_NAME, user.toUtf8()) +
                makeAttr(ATTR_USER_PASSWORD, papPassword(pwd, ra)) +
                makeAttr(ATTR_NAS_IDENT, "nac-bench") +
                makeAttr(ATTR_NAS_IP, QByteArray(4, '\0')) +
                makeAttr(ATTR_CALLING_STATION, station.toUtf8());
            QByteArray pkt = encodeRadius(CODE_ACCESS_REQUEST, workerIdx & 0xFF, ra, body);
            QByteArray resp = exchange(pkt, 3000);
            if (resp.size() >= 1) return resp[0];
            return -1;
        } else {
            QByteArray eap1 = eapIdentity(1, user);
            QByteArray body = makeAttr(ATTR_USER_NAME, user.toUtf8()) +
                makeAttr(ATTR_NAS_IDENT, "nac-bench") +
                makeAttr(ATTR_NAS_IP, QByteArray(4, '\0')) +
                makeAttr(ATTR_CALLING_STATION, station.toUtf8()) +
                makeAttr(ATTR_MSG_AUTH, QByteArray(16, '\0')) +
                makeAttr(ATTR_EAP_MSG, eap1);
            QByteArray pkt = encodeRadius(CODE_ACCESS_REQUEST, workerIdx & 0xFF, ra, body);
            QByteArray resp = exchange(pkt, 3000);
            if (resp.size() < 1) return -1;
            if ((quint8)resp[0] != CODE_ACCESS_CHALLENGE) return resp[0];
            QMap<int, QByteArray> attrs = parseAttrs(resp.mid(20));
            QByteArray eapData = attrs.value(ATTR_EAP_MSG);
            if (eapData.size() < 5) return -1;
            quint8 srvEapId = (quint8)eapData[1];
            QByteArray challenge;
            if (eapData.size() >= 6) {
                int valSize = (quint8)eapData[5];
                challenge = eapData.mid(6, valSize);
            }
            QByteArray eap2 = eapMd5Response(srvEapId, pwd, challenge);
            QByteArray body2 = makeAttr(ATTR_USER_NAME, user.toUtf8()) +
                makeAttr(ATTR_NAS_IDENT, "nac-bench") +
                makeAttr(ATTR_NAS_IP, QByteArray(4, '\0')) +
                makeAttr(ATTR_CALLING_STATION, station.toUtf8()) +
                makeAttr(ATTR_MSG_AUTH, QByteArray(16, '\0')) +
                makeAttr(ATTR_EAP_MSG, eap2);
            QByteArray pkt2 = encodeRadius(CODE_ACCESS_REQUEST, (workerIdx + 1) & 0xFF, ra, body2);
            QByteArray resp2 = exchange(pkt2, 3000);
            if (resp2.size() >= 1) return resp2[0];
            return -1;
        }
    };

    for (int c : levels) {
        if (!running) break;

        outputEdit->append(QString("\n[%1] C=%2 预热 %3s ...").arg(mode).arg(c).arg(warmup));
        QCoreApplication::processEvents();

        // 预热
        QElapsedTimer wt;
        wt.start();
        while (wt.elapsed() < warmup * 1000 && running) {
            for (int i = 0; i < c; i++) doAuth(i);
            QCoreApplication::processEvents();
        }

        outputEdit->append(QString("[%1] C=%2 测量 %3s ...").arg(mode).arg(c).arg(duration));
        QCoreApplication::processEvents();

        // 正式测量
        QElapsedTimer timer;
        timer.start();
        QVector<double> latencies;
        latencies.reserve(c * duration * 2);
        int success = 0, fail = 0, errors = 0;

        while (timer.elapsed() < duration * 1000 && running) {
            for (int i = 0; i < c; i++) {
                qint64 t0 = QDateTime::currentMSecsSinceEpoch();
                int code = doAuth(i);
                qint64 dt = QDateTime::currentMSecsSinceEpoch() - t0;
                latencies.append(dt);
                if (code == CODE_ACCESS_ACCEPT) success++;
                else if (code == CODE_ACCESS_REJECT) fail++;
                else errors++;
            }
            QCoreApplication::processEvents();
        }

        double elapsed = timer.elapsed() / 1000.0;
        int total = success + fail + errors;
        double tps = elapsed > 0 ? total / elapsed : 0;
        double sr = total > 0 ? success * 100.0 / total : 0;

        double avgMs = 0, p50 = 0, p95 = 0, p99 = 0, maxMs = 0;
        if (!latencies.isEmpty()) {
            std::sort(latencies.begin(), latencies.end());
            double sum = 0;
            for (double v : latencies) sum += v;
            avgMs = sum / latencies.size();
            int n = latencies.size();
            p50 = latencies[n * 50 / 100];
            p95 = latencies[std::min(n - 1, n * 95 / 100)];
            p99 = latencies[std::min(n - 1, n * 99 / 100)];
            maxMs = latencies.last();
        }

        outputEdit->append(QString("[%1] C=%2  吞吐=%3 auth/s  成功=%4  失败=%5  错误=%6  成功率=%7%  "
                                   "avg=%8ms  p50=%9ms  p95=%10ms  p99=%11ms  max=%12ms")
            .arg(mode.toUpper())
            .arg(c)
            .arg(tps, 0, 'f', 1)
            .arg(success)
            .arg(fail)
            .arg(errors)
            .arg(sr, 0, 'f', 2)
            .arg(avgMs, 0, 'f', 0)
            .arg(p50, 0, 'f', 0)
            .arg(p95, 0, 'f', 0)
            .arg(p99, 0, 'f', 0)
            .arg(maxMs, 0, 'f', 0));
        QCoreApplication::processEvents();
    }

    outputEdit->append("\n============ 测试完成 ============");
    outputEdit->append(QString("测试时间: %1").arg(QDateTime::currentDateTime().toString("yyyy-MM-dd hh:mm:ss")));
    outputEdit->append(QString("服务器: %1:%2  模式: %3").arg(server).arg(port).arg(mode.toUpper()));
    outputEdit->append(QString("用户: %1").arg(user));

    running = false;
    startBtn->setEnabled(true);
    smokeBtn->setEnabled(true);
}

void MainWindow::onTestProgress(const QString &msg) {
    outputEdit->append(msg);
    QCoreApplication::processEvents();
}

void MainWindow::onTestFinished() {
    outputEdit->append("\n============ 测试完成 ============");
    startBtn->setEnabled(true);
    smokeBtn->setEnabled(true);
    running = false;
}
