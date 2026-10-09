#include "audiooutput.h"
#include <QDebug>

AudioOutput::AudioOutput(QObject *parent) : QObject(parent) {}

AudioOutput::~AudioOutput() { stop(); }

bool AudioOutput::start(int sampleRate, int channels)
{
    stop();

    QAudioFormat fmt;
    fmt.setSampleRate(sampleRate);
    fmt.setChannelCount(channels);
    fmt.setSampleSize(16);
    fmt.setCodec("audio/pcm");
    fmt.setByteOrder(QAudioFormat::LittleEndian);
    fmt.setSampleType(QAudioFormat::SignedInt);

    QAudioDeviceInfo info = QAudioDeviceInfo::defaultOutputDevice();
    if (!info.isFormatSupported(fmt)) {
        fmt = info.nearestFormat(fmt);
        qWarning() << "音频格式不支持，使用最接近格式:"
                   << fmt.sampleRate() << "Hz" << fmt.channelCount() << "ch";
    }

    m_format = fmt;
    m_output = new QAudioOutput(info, fmt, this);
    m_output->setVolume(m_volume.load());
    m_device = m_output->start();
    if (!m_device) {
        qWarning() << "QAudioOutput 启动失败";
        delete m_output;
        m_output = nullptr;
        return false;
    }

    m_clockInited = false;
    m_firstPtsUs = -1;
    return true;
}

void AudioOutput::stop()
{
    if (m_output) {
        m_output->stop();
        m_output->deleteLater();
        m_output = nullptr;
        m_device = nullptr;
    }
    m_clockInited = false;
    m_firstPtsUs = -1;
    m_rate = 1.0f;
}

void AudioOutput::pushPCM(const QByteArray &pcm, int64_t ptsUs)
{
    if (!m_device || pcm.isEmpty()) return;

    {
        QMutexLocker locker(&m_mutex);
        // 初始化主时钟基准（第一帧带有效 PTS 的音频）
        if (!m_clockInited.load() && ptsUs >= 0 && m_output) {
            m_firstPtsUs = ptsUs;
            m_baseProcessedUs = m_output->processedUSecs();
            m_clockInited = true;
        }
    }

    QByteArray out = pcm;
    if (m_muted.load()) out.fill(0, out.size());

    QMutexLocker locker(&m_mutex);
    qint64 written = 0;
    while (written < out.size()) {
        qint64 n = m_device->write(out.constData() + written, out.size() - written);
        if (n <= 0) break;
        written += n;
    }
}

int64_t AudioOutput::clockUs() const
{
    QMutexLocker locker(&m_mutex);
    if (!m_clockInited.load() || !m_output) return -1;
    qint64 processedDelta = m_output->processedUSecs() - m_baseProcessedUs.load();
    // 倍速播放时，设备每处理 1 微秒，实际消费 rate 微秒的原始音频内容
    return m_firstPtsUs.load() + (int64_t)(processedDelta * (double)m_rate.load());
}

void AudioOutput::setRate(float rate)
{
    QMutexLocker locker(&m_mutex);
    // rebase 时钟基准：用旧 rate 计算当前位置，设为新 firstPts，避免时钟跳变
    if (m_clockInited.load() && m_output) {
        qint64 processedDelta = m_output->processedUSecs() - m_baseProcessedUs.load();
        int64_t currentClock = m_firstPtsUs.load() +
                               (int64_t)(processedDelta * (double)m_rate.load());
        m_firstPtsUs = currentClock;
        m_baseProcessedUs = m_output->processedUSecs();
    }
    m_rate = rate;
}

void AudioOutput::resetClock()
{
    QMutexLocker locker(&m_mutex);
    m_clockInited = false;
    m_firstPtsUs = -1;
}

void AudioOutput::suspend()
{
    if (m_output) m_output->suspend();
}

void AudioOutput::resume()
{
    if (m_output) m_output->resume();
}

void AudioOutput::setMuted(bool m) { m_muted = m; }

void AudioOutput::setVolume(float v)
{
    v = qBound(0.0f, v, 1.0f);
    m_volume = v;
    if (m_output) m_output->setVolume(v);
}
