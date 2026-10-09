#include "mainwindow.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QStatusBar>
#include <QMessageBox>
#include <QStyle>
#include <QKeyEvent>
#include <QFont>
#include <QTimer>

static QString timeText(int64_t us)
{
    int totalSec = (int)(us / 1000000);
    int h = totalSec / 3600;
    int m = (totalSec % 3600) / 60;
    int s = totalSec % 60;
    if (h > 0) return QString("%1:%2:%3").arg(h, 2, 10, QChar('0'))
                                  .arg(m, 2, 10, QChar('0'))
                                  .arg(s, 2, 10, QChar('0'));
    return QString("%1:%2").arg(m, 2, 10, QChar('0')).arg(s, 2, 10, QChar('0'));
}

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent)
{
    m_renderer = new VideoRenderer(this);
    m_player   = new VideoPlayer(this);

    setupUi();
    applyDarkTheme();

    connect(m_player, &VideoPlayer::newFrame,
            m_renderer, &VideoRenderer::updateFrame);
    connect(m_player, &VideoPlayer::statusMessage, this, &MainWindow::onStatusMessage);
    connect(m_player, &VideoPlayer::finished, this, &MainWindow::onFinished);
    connect(m_player, &VideoPlayer::error, this, &MainWindow::onError);
    connect(m_player, &VideoPlayer::durationChanged, this, &MainWindow::onDurationChanged);
    connect(m_player, &VideoPlayer::positionChanged, this, &MainWindow::onPositionChanged);
    connect(m_player, &VideoPlayer::audioAvailable, this, &MainWindow::onAudioAvailable);

    m_posTimer = new QTimer(this);
    m_posTimer->setInterval(200);
    connect(m_posTimer, &QTimer::timeout, this, &MainWindow::updatePosition);
}

MainWindow::~MainWindow()
{
    m_player->stop();
}

void MainWindow::applyDarkTheme()
{
    setStyleSheet(QString::fromUtf8(
        "QMainWindow, QWidget { background-color: #1a1a1a; color: #e0e0e0; }"
        "QLineEdit { background: #2a2a2a; border: 1px solid #3a3a3a; border-radius: 4px; "
        "           padding: 4px 8px; color: #e0e0e0; selection-background-color: #0078d7; }"
        "QLineEdit:focus { border: 1px solid #0078d7; }"
        "QPushButton { background: #2a2a2a; border: 1px solid #3a3a3a; border-radius: 4px; "
        "              padding: 5px 12px; color: #e0e0e0; }"
        "QPushButton:hover { background: #0078d7; border-color: #0078d7; }"
        "QPushButton:pressed { background: #005a9e; }"
        "QPushButton:disabled { color: #555; }"
        "QSlider::groove:horizontal { border: none; height: 4px; background: #333; border-radius: 2px; }"
        "QSlider::sub-page:horizontal { background: #0078d7; border-radius: 2px; }"
        "QSlider::handle:horizontal { background: #fff; width: 12px; height: 12px; "
        "           margin: -5px 0; border-radius: 6px; }"
        "QSlider::handle:horizontal:hover { background: #0078d7; }"
        "QComboBox { background: #2a2a2a; border: 1px solid #3a3a3a; border-radius: 4px; "
        "           padding: 3px 8px; color: #e0e0e0; min-width: 50px; }"
        "QComboBox:hover { border-color: #0078d7; }"
        "QComboBox QAbstractItemView { background: #2a2a2a; border: 1px solid #3a3a3a; "
        "           color: #e0e0e0; selection-background-color: #0078d7; }"
        "QLabel { color: #e0e0e0; }"
        "QStatusBar { background: #111; color: #888; }"
        "QStatusBar::item { border: none; }"
    ));

    // 渲染区黑色
    m_renderer->setStyleSheet("background: black;");
}

void MainWindow::setupUi()
{
    setWindowTitle(QString::fromUtf8("迅雷风格远程视频播放器"));
    resize(1200, 750);

    QStyle *st = style();

    // ---- 顶部 URL 栏 ----
    QWidget *topBar = new QWidget;
    topBar->setFixedHeight(38);
    QHBoxLayout *topLayout = new QHBoxLayout(topBar);
    topLayout->setContentsMargins(4, 2, 4, 2);
    topLayout->setSpacing(4);

    m_urlEdit = new QLineEdit;
    m_urlEdit->setPlaceholderText(QString::fromUtf8(
        "输入视频地址: rtsp:// http://...mp4 http://...m3u8"));
    m_openBtn = new QPushButton(QString::fromUtf8("打开"));
    m_openBtn->setFixedWidth(60);
    topLayout->addWidget(m_urlEdit, 1);
    topLayout->addWidget(m_openBtn);

    // ---- 底部控制栏 ----
    QWidget *ctrlBar = new QWidget;
    ctrlBar->setFixedHeight(48);
    ctrlBar->setStyleSheet("background: #111;");
    QHBoxLayout *ctrlLayout = new QHBoxLayout(ctrlBar);
    ctrlLayout->setContentsMargins(8, 4, 8, 4);
    ctrlLayout->setSpacing(6);

    m_backwardBtn = new QPushButton;
    m_backwardBtn->setIcon(st->standardIcon(QStyle::SP_MediaSeekBackward));
    m_backwardBtn->setFixedSize(32, 32);
    m_backwardBtn->setToolTip(QString::fromUtf8("后退 10 秒"));

    m_playBtn = new QPushButton;
    m_playBtn->setIcon(st->standardIcon(QStyle::SP_MediaPlay));
    m_playBtn->setFixedSize(36, 36);
    m_playBtn->setToolTip(QString::fromUtf8("播放 / 暂停 (空格)"));

    m_forwardBtn = new QPushButton;
    m_forwardBtn->setIcon(st->standardIcon(QStyle::SP_MediaSeekForward));
    m_forwardBtn->setFixedSize(32, 32);
    m_forwardBtn->setToolTip(QString::fromUtf8("快进 10 秒"));

    m_stopBtn = new QPushButton;
    m_stopBtn->setIcon(st->standardIcon(QStyle::SP_MediaStop));
    m_stopBtn->setFixedSize(32, 32);
    m_stopBtn->setToolTip(QString::fromUtf8("停止"));

    m_timeLabel = new QLabel("00:00 / 00:00");
    m_timeLabel->setFixedWidth(120);
    m_timeLabel->setAlignment(Qt::AlignCenter);
    QFont mono("Consolas", 9);
    m_timeLabel->setFont(mono);

    m_progress = new QSlider(Qt::Horizontal);
    m_progress->setRange(0, 1000);
    m_progress->setEnabled(false);

    m_muteBtn = new QPushButton;
    m_muteBtn->setIcon(st->standardIcon(QStyle::SP_MediaVolume));
    m_muteBtn->setFixedSize(28, 28);
    m_muteBtn->setToolTip(QString::fromUtf8("静音 / 取消静音 (M)"));
    m_muteBtn->setCheckable(true);

    m_volume = new QSlider(Qt::Horizontal);
    m_volume->setRange(0, 100);
    m_volume->setValue(80);
    m_volume->setFixedWidth(80);

    m_speedCombo = new QComboBox;
    m_speedCombo->addItems({"0.5x", "1.0x", "1.5x", "2.0x", "4.0x"});
    m_speedCombo->setCurrentIndex(1);
    m_speedCombo->setFixedWidth(65);

    m_fullscreenBtn = new QPushButton;
    m_fullscreenBtn->setIcon(st->standardIcon(QStyle::SP_TitleBarMaxButton));
    m_fullscreenBtn->setFixedSize(28, 28);
    m_fullscreenBtn->setToolTip(QString::fromUtf8("全屏 (F11 / Esc 退出)"));

    m_hwLabel = new QLabel("软件解码");
    m_hwLabel->setFixedWidth(80);
    m_hwLabel->setAlignment(Qt::AlignRight | Qt::AlignVCenter);
    QFont smallFont;
    smallFont.setPointSize(8);
    m_hwLabel->setFont(smallFont);

    ctrlLayout->addWidget(m_backwardBtn);
    ctrlLayout->addWidget(m_playBtn);
    ctrlLayout->addWidget(m_forwardBtn);
    ctrlLayout->addWidget(m_stopBtn);
    ctrlLayout->addWidget(m_timeLabel);
    ctrlLayout->addWidget(m_progress, 1);
    ctrlLayout->addWidget(m_muteBtn);
    ctrlLayout->addWidget(m_volume);
    ctrlLayout->addWidget(m_speedCombo);
    ctrlLayout->addWidget(m_fullscreenBtn);
    ctrlLayout->addWidget(m_hwLabel);

    // ---- 组装 ----
    QWidget *central = new QWidget;
    QVBoxLayout *mainLayout = new QVBoxLayout(central);
    mainLayout->setContentsMargins(0, 0, 0, 0);
    mainLayout->setSpacing(0);
    mainLayout->addWidget(topBar);
    mainLayout->addWidget(m_renderer, 1);
    mainLayout->addWidget(ctrlBar);
    setCentralWidget(central);

    // 状态栏
    m_statusLabel = new QLabel(QString::fromUtf8("就绪"));
    statusBar()->addWidget(m_statusLabel, 1);
    statusBar()->setSizeGripEnabled(false);

    // ---- 信号连接 ----
    connect(m_openBtn, &QPushButton::clicked, this, [this]() {
        if (!m_urlEdit->text().trimmed().isEmpty())
            playUrl(m_urlEdit->text().trimmed());
    });
    connect(m_playBtn, &QPushButton::clicked, this, &MainWindow::onPlayPauseClicked);
    connect(m_stopBtn, &QPushButton::clicked, this, &MainWindow::onStopClicked);
    connect(m_backwardBtn, &QPushButton::clicked, this, &MainWindow::onSeekBackward);
    connect(m_forwardBtn, &QPushButton::clicked, this, &MainWindow::onSeekForward);
    connect(m_progress, &QSlider::sliderPressed, this, &MainWindow::onSliderPressed);
    connect(m_progress, &QSlider::sliderMoved, this, &MainWindow::onSliderMoved);
    connect(m_progress, &QSlider::sliderReleased, this, &MainWindow::onSliderReleased);
    connect(m_volume, &QSlider::valueChanged, this, &MainWindow::onVolumeChanged);
    connect(m_muteBtn, &QPushButton::toggled, this, &MainWindow::onMuteClicked);
    connect(m_speedCombo, static_cast<void(QComboBox::*)(int)>(&QComboBox::currentIndexChanged),
            this, &MainWindow::onSpeedChanged);
    connect(m_fullscreenBtn, &QPushButton::clicked, this, &MainWindow::onFullscreenClicked);
}

void MainWindow::playUrl(const QString &url)
{
    if (url.trimmed().isEmpty()) return;
    m_urlEdit->setText(url.trimmed());
    m_urlEdit->setEnabled(false);
    m_openBtn->setEnabled(false);

    m_playBtn->setIcon(style()->standardIcon(QStyle::SP_MediaPause));
    m_isPlaying = true;
    m_progress->setEnabled(true);

    m_player->setVolume(m_volume->value() / 100.0f);
    m_speedCombo->blockSignals(true);
    m_speedCombo->setCurrentIndex(1);  // 1.0x
    m_speedCombo->blockSignals(false);
    m_player->open(url.trimmed());

    m_posTimer->start();
}

void MainWindow::onPlayPauseClicked()
{
    if (!m_isPlaying) {
        // 当前已停止：如果 URL 有值则重新播放
        if (!m_urlEdit->text().trimmed().isEmpty()) {
            playUrl(m_urlEdit->text().trimmed());
        }
        return;
    }

    bool willPause = !m_player->isPaused();
    m_player->setPause(willPause);
    m_playBtn->setIcon(style()->standardIcon(
        willPause ? QStyle::SP_MediaPlay : QStyle::SP_MediaPause));

    if (willPause) m_posTimer->stop();
    else m_posTimer->start();
}

void MainWindow::onStopClicked()
{
    m_player->stop();
    onFinished();
}

void MainWindow::onSeekBackward()
{
    m_player->seekBy(-10LL * 1000000);
}

void MainWindow::onSeekForward()
{
    m_player->seekBy(10LL * 1000000);
}

void MainWindow::onSliderPressed()
{
    m_sliderDragging = true;
    m_wasPausedBeforeDrag = m_player->isPaused();
    if (!m_wasPausedBeforeDrag) m_player->setPause(true);
}

void MainWindow::onSliderMoved(int value)
{
    if (m_duration <= 0) return;
    int64_t targetUs = (int64_t)value * m_duration / 1000;
    m_timeLabel->setText(timeText(targetUs) + " / " + timeText(m_duration));
}

void MainWindow::onSliderReleased()
{
    if (m_duration > 0) {
        int64_t targetUs = (int64_t)m_progress->value() * m_duration / 1000;
        m_player->seekTo(targetUs);
    }
    m_sliderDragging = false;
    if (!m_wasPausedBeforeDrag) m_player->setPause(false);
}

void MainWindow::onVolumeChanged(int v)
{
    m_player->setVolume(v / 100.0f);
    if (m_muteBtn->isChecked() && v > 0) {
        m_muteBtn->setChecked(false);
    }
}

void MainWindow::onMuteClicked()
{
    bool muted = m_muteBtn->isChecked();
    m_player->setMuted(muted);
    m_muteBtn->setIcon(style()->standardIcon(
        muted ? QStyle::SP_MediaVolumeMuted : QStyle::SP_MediaVolume));
}

void MainWindow::onSpeedChanged(int index)
{
    static const float speeds[] = {0.5f, 1.0f, 1.5f, 2.0f, 4.0f};
    if (index >= 0 && index < 5) {
        m_player->setPlaybackRate(speeds[index]);
    }
}

void MainWindow::onFullscreenClicked()
{
    if (isFullScreen()) {
        showNormal();
        m_fullscreenBtn->setIcon(style()->standardIcon(QStyle::SP_TitleBarMaxButton));
    } else {
        showFullScreen();
        m_fullscreenBtn->setIcon(style()->standardIcon(QStyle::SP_TitleBarMinButton));
    }
}

void MainWindow::onStatusMessage(const QString &msg)
{
    m_statusLabel->setText(msg);
}

void MainWindow::onFinished()
{
    m_isPlaying = false;
    m_playBtn->setIcon(style()->standardIcon(QStyle::SP_MediaPlay));
    m_urlEdit->setEnabled(true);
    m_openBtn->setEnabled(true);
    m_progress->setEnabled(false);
    m_posTimer->stop();
    m_statusLabel->setText(QString::fromUtf8("已停止"));
}

void MainWindow::onError(const QString &msg)
{
    m_statusLabel->setText(QString::fromUtf8("错误: ") + msg);
    QMessageBox::critical(this, QString::fromUtf8("播放错误"), msg);
    onFinished();
}

void MainWindow::onDurationChanged(int64_t us)
{
    m_duration = us;
    m_timeLabel->setText("00:00 / " + timeText(us));
}

void MainWindow::onPositionChanged(int64_t us)
{
    if (m_sliderDragging) return;
    if (m_duration > 0) {
        int pos = (int)(us * 1000 / m_duration);
        m_progress->blockSignals(true);
        m_progress->setValue(pos);
        m_progress->blockSignals(false);
    }
    m_timeLabel->setText(timeText(us) + " / " + timeText(m_duration));
}

void MainWindow::onAudioAvailable(bool yes)
{
    if (yes) {
        m_player->setVolume(m_volume->value() / 100.0f);
    }
}

void MainWindow::updatePosition()
{
    if (m_sliderDragging) return;
    int64_t pos = m_player->currentPosUs();
    if (pos >= 0) {
        if (m_duration > 0) {
            m_progress->blockSignals(true);
            m_progress->setValue((int)(pos * 1000 / m_duration));
            m_progress->blockSignals(false);
        }
        m_timeLabel->setText(timeText(pos) + " / " + timeText(m_duration));
    }
    m_hwLabel->setText(m_player->isHardwareDecoding()
                       ? m_player->hwDecoderName()
                       : QString::fromUtf8("软件解码"));
}

void MainWindow::keyPressEvent(QKeyEvent *event)
{
    switch (event->key()) {
    case Qt::Key_Space:
        onPlayPauseClicked();
        break;
    case Qt::Key_Left:
        onSeekBackward();
        break;
    case Qt::Key_Right:
        onSeekForward();
        break;
    case Qt::Key_F11:
        onFullscreenClicked();
        break;
    case Qt::Key_Escape:
        if (isFullScreen()) onFullscreenClicked();
        break;
    case Qt::Key_M:
        m_muteBtn->setChecked(!m_muteBtn->isChecked());
        break;
    default:
        QMainWindow::keyPressEvent(event);
    }
}
