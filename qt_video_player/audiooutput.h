#ifndef AUDIOOUTPUT_H
#define AUDIOOUTPUT_H

#include <QObject>
#include <QAudioFormat>
#include <QAudioOutput>
#include <QAudioDeviceInfo>
#include <QIODevice>
#include <QByteArray>
#include <QMutex>
#include <atomic>

/**
 * @brief 音频输出 + 主时钟
 *
 * 封装 Qt Multimedia 的 QAudioOutput（push 模式），同时维护音频主时钟：
 *   - 第一帧 PCM 入队时记录 ptsUs 与 processedUSecs() 基准
 *   - clockUs() 返回当前音频播放位置（微秒），供视频帧同步使用
 *   - 支持 suspend/resume（暂停时音频时钟冻结）
 */
class AudioOutput : public QObject
{
    Q_OBJECT
public:
    explicit AudioOutput(QObject *parent = nullptr);
    ~AudioOutput() override;

    /** 以指定采样率/声道数启动音频设备（S16 交错 PCM） */
    bool start(int sampleRate, int channels);
    /** 停止并释放音频设备 */
    void stop();
    /** 推送一段 PCM 数据（ptsUs 为该段起始 PTS，微秒，AV_NOPTS_VALUE 表示未知） */
    void pushPCM(const QByteArray &pcm, int64_t ptsUs);
    /** 当前音频主时钟位置（微秒）；未初始化或无音频时返回 -1 */
    int64_t clockUs() const;
    /** 重置主时钟基准（seek 后调用） */
    void resetClock();
    /** 暂停/恢复音频播放 */
    void suspend();
    void resume();

    void setMuted(bool m);
    void setVolume(float v);   // 0.0 ~ 1.0
    /** 设置倍速并在时钟基准上 rebase，避免跳变 */
    void setRate(float rate);
    float rate() const { return m_rate.load(); }
    bool isActive() const { return m_output != nullptr; }
    int sampleRate() const { return m_format.sampleRate(); }
    int channelCount() const { return m_format.channelCount(); }

private:
    QAudioOutput *m_output = nullptr;
    QIODevice   *m_device = nullptr;
    QAudioFormat m_format;

    std::atomic<qint64> m_firstPtsUs{-1};
    std::atomic<qint64> m_baseProcessedUs{0};
    std::atomic<bool>   m_clockInited{false};
    std::atomic<bool>   m_muted{false};
    std::atomic<float>  m_volume{1.0f};
    std::atomic<float>  m_rate{1.0f};
    mutable QMutex m_mutex;
};

#endif // AUDIOOUTPUT_H
