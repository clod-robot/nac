#ifndef VIDEOPLAYER_H
#define VIDEOPLAYER_H

#include <QObject>
#include <QMutex>
#include <QWaitCondition>
#include <QQueue>
#include <QImage>
#include <QAtomicInt>
#include <QString>
#include <thread>
#include <atomic>
#include <chrono>
#include "audiooutput.h"

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/hwcontext.h>
#include <libavutil/imgutils.h>
#include <libavutil/channel_layout.h>
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>
}

// 线程安全的 AVPacket 队列，限制最大长度防止内存爆炸
class PacketQueue {
public:
    PacketQueue(int maxSize = 512) : m_maxSize(maxSize), m_abort(false) {}

    void push(AVPacket *pkt) {
        QMutexLocker locker(&m_mutex);
        while (m_queue.size() >= m_maxSize && !m_abort) {
            m_notFull.wait(&m_mutex);
        }
        if (m_abort) return;
        m_queue.enqueue(pkt);
        m_notEmpty.wakeOne();
    }

    AVPacket *pop() {
        QMutexLocker locker(&m_mutex);
        while (m_queue.isEmpty() && !m_abort) {
            m_notEmpty.wait(&m_mutex);
        }
        if (m_abort) return nullptr;
        AVPacket *pkt = m_queue.dequeue();
        m_notFull.wakeOne();
        return pkt;
    }

    void abort() {
        QMutexLocker locker(&m_mutex);
        m_abort = true;
        m_notEmpty.wakeAll();
        m_notFull.wakeAll();
    }

    void clear() {
        QMutexLocker locker(&m_mutex);
        while (!m_queue.isEmpty()) {
            AVPacket *pkt = m_queue.dequeue();
            av_packet_free(&pkt);
        }
        m_notFull.wakeAll();
    }

    int size() {
        QMutexLocker locker(&m_mutex);
        return m_queue.size();
    }

private:
    QQueue<AVPacket*> m_queue;
    int m_maxSize;
    bool m_abort;
    QMutex m_mutex;
    QWaitCondition m_notEmpty;
    QWaitCondition m_notFull;
};

/**
 * @brief 远程视频播放器核心
 *
 * 架构：
 *   - Demuxer 线程：av_read_frame 读取音视频包，分别放入 video/audio PacketQueue
 *   - Video Decoder 线程：硬件解码视频，按音频主时钟同步后发送 newFrame
 *   - Audio Decoder 线程：解码音频，swresample 重采样后推送到 AudioOutput
 *   - UI 线程：VideoRenderer 用 OpenGL 渲染 QImage
 *
 * A/V 同步：以音频为主时钟（AudioOutput::clockUs），视频帧等待音频时钟到达后渲染。
 *          无音频流时回退到系统时钟。
 */
class VideoPlayer : public QObject
{
    Q_OBJECT

public:
    explicit VideoPlayer(QObject *parent = nullptr);
    ~VideoPlayer();

    bool isHardwareDecoding() const { return m_hwEnabled; }
    QString hwDecoderName() const { return m_hwDecoderName; }

    /** 总时长（微秒），未知返回 0 */
    int64_t durationUs() const { return m_durationUs.load(); }
    /** 当前播放位置（微秒） */
    int64_t currentPosUs() const;
    bool hasAudio() const { return m_audioStreamIndex >= 0; }
    float playbackRate() const { return m_rate.load(); }
    bool isPaused() const { return m_paused.load(); }

public slots:
    void open(const QString &url);
    void stop();
    /** 跳转到绝对位置（微秒） */
    void seekTo(int64_t targetUs);
    /** 相对跳转（微秒，正=快进，负=后退） */
    void seekBy(int64_t deltaUs);
    /** 设置倍速（0.5 ~ 4.0） */
    void setPlaybackRate(float rate);
    /** 暂停/恢复 */
    void setPause(bool paused);
    void setVolume(float v);
    void setMuted(bool m);

signals:
    void newFrame(const QImage &image);
    void statusMessage(const QString &msg);
    void finished();
    void error(const QString &msg);
    void durationChanged(int64_t us);
    void positionChanged(int64_t us);
    void audioAvailable(bool yes);

private:
    void runDemuxer();
    void runVideoDecoder();
    void runAudioDecoder();

    bool openInputStream();
    bool setupHardwareDecoder();
    AVFrame *decodeVideoPacket(AVPacket *pkt, bool &gotFrame);
    AVFrame *decodeAudioPacket(AVPacket *pkt, bool &gotFrame);
    QImage frameToImage(AVFrame *frame);
    QByteArray resampleAudio(AVFrame *frame, int64_t &ptsUs);
    void waitAVSync(AVFrame *frame);
    void cleanup();
    AVPixelFormat findHwPixFmt(const AVCodec *codec, AVHWDeviceType type);
    int64_t ptsToUs(int64_t pts, AVRational tb) const;

private:
    // FFmpeg 句柄
    AVFormatContext *m_fmtCtx = nullptr;
    AVCodecContext  *m_videoCodecCtx = nullptr;
    AVCodecContext  *m_audioCodecCtx = nullptr;
    AVBufferRef     *m_hwDeviceCtx = nullptr;
    SwsContext      *m_swsCtx = nullptr;
    SwrContext      *m_swrCtx = nullptr;

    int m_videoStreamIndex = -1;
    int m_audioStreamIndex = -1;
    AVPixelFormat m_hwPixFmt = AV_PIX_FMT_NONE;

    // sws 上下文参数跟踪
    int m_swsW = 0, m_swsH = 0;
    AVPixelFormat m_swsFmt = AV_PIX_FMT_NONE;

    // swr 输出参数跟踪（变化时重建）
    int m_swrOutRate = 0;
    int m_swrOutCh = 0;
    AVSampleFormat m_swrOutFmt = AV_SAMPLE_FMT_NONE;
    int m_swrInRate = 0;
    int m_swrInCh = 0;
    AVSampleFormat m_swrInFmt = AV_SAMPLE_FMT_NONE;

    // 视频流时基（无音频时用系统时钟同步）
    AVRational m_videoTimeBase = {0, 1};
    bool m_vClockInited = false;
    int64_t m_vFirstPts = 0;
    std::chrono::steady_clock::time_point m_vClockStart;

    // 线程
    std::thread m_demuxerThread;
    std::thread m_videoThread;
    std::thread m_audioThread;
    PacketQueue *m_videoQueue = nullptr;
    PacketQueue *m_audioQueue = nullptr;

    AudioOutput *m_audioOutput = nullptr;

    QAtomicInt m_running;
    std::atomic<bool> m_paused{false};
    std::atomic<float> m_rate{1.0f};
    std::atomic<int64_t> m_seekTargetUs{-1};   // -1 = 无 seek 请求
    std::atomic<int64_t> m_durationUs{0};
    std::atomic<int> m_flushSeq{0};            // seek 后递增，通知解码器 flush
    std::atomic<int64_t> m_lastVideoPtsUs{0};  // 用于上报 position

    bool m_hwEnabled = false;
    QString m_hwDecoderName = "software";
    QString m_url;
};

#endif // VIDEOPLAYER_H
