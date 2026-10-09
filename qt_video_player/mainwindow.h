#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QMainWindow>
#include <QLineEdit>
#include <QPushButton>
#include <QLabel>
#include <QSlider>
#include <QComboBox>
#include <QTimer>
#include "videoplayer.h"
#include "videorenderer.h"

/**
 * @brief 迅雷风格播放器主窗口
 *
 * 布局：
 *   顶部：URL 输入 + 打开按钮（可隐藏）
 *   中央：视频渲染区（黑色背景）
 *   底部控制栏：快退 / 播放暂停 / 快进 / 时间 / 进度条 / 音量 / 倍速 / 全屏
 */
class MainWindow : public QMainWindow
{
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = nullptr);
    ~MainWindow() override;

    void playUrl(const QString &url);

protected:
    void keyPressEvent(QKeyEvent *event) override;

private slots:
    void onPlayPauseClicked();
    void onStopClicked();
    void onSeekBackward();
    void onSeekForward();
    void onSliderPressed();
    void onSliderMoved(int value);
    void onSliderReleased();
    void onVolumeChanged(int v);
    void onMuteClicked();
    void onSpeedChanged(int index);
    void onFullscreenClicked();
    void onStatusMessage(const QString &msg);
    void onFinished();
    void onError(const QString &msg);
    void onDurationChanged(int64_t us);
    void onPositionChanged(int64_t us);
    void onAudioAvailable(bool yes);
    void updatePosition();

private:
    void setupUi();
    void applyDarkTheme();

    VideoPlayer   *m_player   = nullptr;
    VideoRenderer *m_renderer = nullptr;

    // 顶部
    QLineEdit   *m_urlEdit   = nullptr;
    QPushButton *m_openBtn   = nullptr;

    // 底部控制
    QPushButton *m_backwardBtn = nullptr;  // 快退 10s
    QPushButton *m_playBtn     = nullptr;  // 播放/暂停
    QPushButton *m_forwardBtn  = nullptr;  // 快进 10s
    QPushButton *m_stopBtn     = nullptr;
    QLabel      *m_timeLabel   = nullptr;  // 当前 / 总时长
    QSlider     *m_progress    = nullptr;
    QPushButton *m_muteBtn     = nullptr;
    QSlider     *m_volume      = nullptr;
    QComboBox   *m_speedCombo  = nullptr;
    QPushButton *m_fullscreenBtn = nullptr;
    QLabel      *m_hwLabel     = nullptr;
    QLabel      *m_statusLabel = nullptr;

    // 状态
    bool m_isPlaying = false;
    bool m_sliderDragging = false;
    bool m_wasPausedBeforeDrag = false;
    int64_t m_duration = 0;

    QTimer *m_posTimer = nullptr;  // 定期刷新进度
};

#endif // MAINWINDOW_H
