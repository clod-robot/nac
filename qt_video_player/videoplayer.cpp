#include "videoplayer.h"
#include <QDebug>
#include <QCoreApplication>

VideoPlayer::VideoPlayer(QObject *parent)
    : QObject(parent)
{
    m_videoQueue = new PacketQueue(512);
    m_audioQueue = new PacketQueue(512);
    m_audioOutput = new AudioOutput(this);
}

VideoPlayer::~VideoPlayer()
{
    stop();
    delete m_videoQueue;
    delete m_audioQueue;
}

// ---- PTS 转换 ----
int64_t VideoPlayer::ptsToUs(int64_t pts, AVRational tb) const
{
    if (pts == AV_NOPTS_VALUE || tb.num == 0 || tb.den == 0) return -1;
    return av_rescale_q(pts, tb, AVRational{1, 1000000});
}

AVPixelFormat VideoPlayer::findHwPixFmt(const AVCodec *codec, AVHWDeviceType type)
{
    for (int i = 0;; i++) {
        const AVCodecHWConfig *config = avcodec_get_hw_config(codec, i);
        if (!config) break;
        if (config->methods & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX &&
            config->device_type == type) {
            return config->pix_fmt;
        }
    }
    return AV_PIX_FMT_NONE;
}

bool VideoPlayer::setupHardwareDecoder()
{
    const AVCodec *codec = m_videoCodecCtx->codec;
    if (!codec) return false;

    // 按优先级排列，自动选择第一个可用的硬件解码器
    // D3D11VA: Win8+，大多数 GPU 支持，零拷贝效率最高
    // DXVA2  : Win7+，最广泛兼容（Win7 没有 D3D11）
    // CUDA   : NVIDIA GPU 专用，需驱动支持（唯一在 WinXP 可用的硬解）
    // QSV    : Intel GPU 专用，需 Media SDK
    AVHWDeviceType types[] = {
        AV_HWDEVICE_TYPE_D3D11VA,
        AV_HWDEVICE_TYPE_DXVA2,
        AV_HWDEVICE_TYPE_CUDA,
        AV_HWDEVICE_TYPE_QSV
    };

    for (AVHWDeviceType type : types) {
        m_hwPixFmt = findHwPixFmt(codec, type);
        if (m_hwPixFmt == AV_PIX_FMT_NONE) {
            emit statusMessage(QString("解码器不支持 %1，尝试下一个")
                               .arg(QString::fromUtf8(av_hwdevice_get_type_name(type))));
            continue;
        }

        AVBufferRef *deviceCtx = nullptr;
        int ret = av_hwdevice_ctx_create(&deviceCtx, type, nullptr, nullptr, 0);
        if (ret < 0) {
            emit statusMessage(QString("%1 设备创建失败，尝试下一个")
                               .arg(QString::fromUtf8(av_hwdevice_get_type_name(type))));
            continue;
        }

        m_videoCodecCtx->hw_device_ctx = av_buffer_ref(deviceCtx);
        m_hwDeviceCtx = deviceCtx;
        m_hwEnabled = true;
        m_hwDecoderName = QString::fromUtf8(av_hwdevice_get_type_name(type));
        emit statusMessage(QString("硬件解码已启用: %1").arg(m_hwDecoderName));
        return true;
    }

    m_hwEnabled = false;
    m_hwDecoderName = "software";
    m_hwPixFmt = AV_PIX_FMT_NONE;
    emit statusMessage("所有硬件解码不可用，使用软件解码");
    return false;
}

bool VideoPlayer::openInputStream()
{
    AVDictionary *options = nullptr;
    const QString url = m_url;
    const bool isHls = url.contains(".m3u8", Qt::CaseInsensitive);
    const bool isNetwork = url.startsWith("rtsp://", Qt::CaseInsensitive) ||
                           url.startsWith("http://", Qt::CaseInsensitive) ||
                           url.startsWith("https://", Qt::CaseInsensitive);

    if (isNetwork) {
        av_dict_set(&options, "analyzeduration", "2000000", 0);
        av_dict_set(&options, "probesize", "1000000", 0);
    }

    if (url.startsWith("rtsp://", Qt::CaseInsensitive)) {
        av_dict_set(&options, "rtsp_transport", "tcp", 0);
        av_dict_set(&options, "stimeout", "5000000", 0);
    } else if (url.startsWith("http://", Qt::CaseInsensitive) ||
               url.startsWith("https://", Qt::CaseInsensitive)) {
        av_dict_set(&options, "rw_timeout", "8000000", 0);
        av_dict_set(&options, "reconnect", "1", 0);
        av_dict_set(&options, "reconnect_streamed", "1", 0);
        av_dict_set(&options, "reconnect_on_network_error", "1", 0);
        av_dict_set(&options, "reconnect_delay_max", "5", 0);
        if (isHls) av_dict_set(&options, "fflags", "nobuffer", 0);
    }

    AVInputFormat *inputFmt = nullptr;
    if (isHls) inputFmt = const_cast<AVInputFormat*>(av_find_input_format("hls"));

    int ret = avformat_open_input(&m_fmtCtx, url.toUtf8().constData(), inputFmt, &options);
    av_dict_free(&options);
    if (ret < 0) {
        char errbuf[256];
        av_strerror(ret, errbuf, sizeof(errbuf));
        emit error(QString("无法打开输入: %1").arg(QString::fromUtf8(errbuf)));
        return false;
    }

    ret = avformat_find_stream_info(m_fmtCtx, nullptr);
    if (ret < 0) {
        emit error("无法获取流信息");
        return false;
    }

    // 总时长
    if (m_fmtCtx->duration != AV_NOPTS_VALUE) {
        m_durationUs = m_fmtCtx->duration;
        emit durationChanged(m_durationUs.load());
    }

    // 视频流
    m_videoStreamIndex = av_find_best_stream(m_fmtCtx, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    if (m_videoStreamIndex < 0) {
        emit error("未找到视频流");
        return false;
    }

    AVStream *vstream = m_fmtCtx->streams[m_videoStreamIndex];
    m_videoTimeBase = vstream->time_base;

    // 探测后 seek 回起点（点播流）
    if (m_fmtCtx->duration != AV_NOPTS_VALUE) {
        int64_t startTs = (vstream->start_time != AV_NOPTS_VALUE) ? vstream->start_time : 0;
        av_seek_frame(m_fmtCtx, m_videoStreamIndex, startTs, AVSEEK_FLAG_BACKWARD);
    }

    // 打开视频解码器
    const AVCodec *vcodec = avcodec_find_decoder(vstream->codecpar->codec_id);
    if (!vcodec) { emit error("找不到视频解码器"); return false; }
    m_videoCodecCtx = avcodec_alloc_context3(vcodec);
    avcodec_parameters_to_context(m_videoCodecCtx, vstream->codecpar);
    {
        unsigned int hw = std::thread::hardware_concurrency();
        m_videoCodecCtx->thread_count = hw > 0 ? (int)hw : 4;
    }
    m_videoCodecCtx->thread_type = FF_THREAD_FRAME | FF_THREAD_SLICE;
    setupHardwareDecoder();
    ret = avcodec_open2(m_videoCodecCtx, vcodec, nullptr);
    if (ret < 0) {
        if (m_hwDeviceCtx) {
            av_buffer_unref(&m_hwDeviceCtx);
            av_buffer_unref(&m_videoCodecCtx->hw_device_ctx);
            m_hwEnabled = false; m_hwDecoderName = "software"; m_hwPixFmt = AV_PIX_FMT_NONE;
            emit statusMessage("硬件解码打开失败，回退软件解码");
            ret = avcodec_open2(m_videoCodecCtx, vcodec, nullptr);
        }
        if (ret < 0) {
            char errbuf[256]; av_strerror(ret, errbuf, sizeof(errbuf));
            emit error(QString("打开视频解码器失败: %1").arg(QString::fromUtf8(errbuf)));
            return false;
        }
    }

    // 音频流
    m_audioStreamIndex = av_find_best_stream(m_fmtCtx, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
    if (m_audioStreamIndex >= 0) {
        AVStream *astream = m_fmtCtx->streams[m_audioStreamIndex];
        const AVCodec *acodec = avcodec_find_decoder(astream->codecpar->codec_id);
        if (acodec) {
            m_audioCodecCtx = avcodec_alloc_context3(acodec);
            avcodec_parameters_to_context(m_audioCodecCtx, astream->codecpar);
            if (avcodec_open2(m_audioCodecCtx, acodec, nullptr) >= 0) {
                int sr = m_audioCodecCtx->sample_rate > 0 ? m_audioCodecCtx->sample_rate : 44100;
                int ch = m_audioCodecCtx->ch_layout.nb_channels > 0 ? m_audioCodecCtx->ch_layout.nb_channels : 2;
                if (m_audioOutput->start(sr, ch)) {
                    emit audioAvailable(true);
                    emit statusMessage(QString("音频: %1Hz %2ch").arg(sr).arg(ch));
                }
            } else {
                avcodec_free_context(&m_audioCodecCtx);
                m_audioStreamIndex = -1;
            }
        }
    }

    return true;
}

AVFrame *VideoPlayer::decodeVideoPacket(AVPacket *pkt, bool &gotFrame)
{
    gotFrame = false;
    int ret = avcodec_send_packet(m_videoCodecCtx, pkt);
    if (ret < 0 && ret != AVERROR_EOF) return nullptr;

    AVFrame *frame = av_frame_alloc();
    ret = avcodec_receive_frame(m_videoCodecCtx, frame);
    if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
        av_frame_free(&frame);
        return nullptr;
    } else if (ret < 0) {
        av_frame_free(&frame);
        return nullptr;
    }

    gotFrame = true;

    if (m_hwEnabled && frame->format == m_hwPixFmt) {
        AVFrame *swFrame = av_frame_alloc();
        ret = av_hwframe_transfer_data(swFrame, frame, 0);
        if (ret < 0) {
            av_frame_free(&swFrame);
            av_frame_free(&frame);
            return nullptr;
        }
        av_frame_copy_props(swFrame, frame);
        av_frame_free(&frame);
        return swFrame;
    }
    return frame;
}

AVFrame *VideoPlayer::decodeAudioPacket(AVPacket *pkt, bool &gotFrame)
{
    gotFrame = false;
    if (!m_audioCodecCtx) return nullptr;

    int ret = avcodec_send_packet(m_audioCodecCtx, pkt);
    if (ret < 0 && ret != AVERROR_EOF) return nullptr;

    AVFrame *frame = av_frame_alloc();
    ret = avcodec_receive_frame(m_audioCodecCtx, frame);
    if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
        av_frame_free(&frame);
        return nullptr;
    } else if (ret < 0) {
        av_frame_free(&frame);
        return nullptr;
    }
    gotFrame = true;
    return frame;
}

QImage VideoPlayer::frameToImage(AVFrame *frame)
{
    int w = frame->width, h = frame->height;
    if (w != m_swsW || h != m_swsH || frame->format != m_swsFmt || !m_swsCtx) {
        if (m_swsCtx) sws_freeContext(m_swsCtx);
        m_swsCtx = sws_getContext(w, h, (AVPixelFormat)frame->format,
                                  w, h, AV_PIX_FMT_RGBA, SWS_BILINEAR,
                                  nullptr, nullptr, nullptr);
        m_swsW = w; m_swsH = h; m_swsFmt = (AVPixelFormat)frame->format;
    }
    if (!m_swsCtx) return QImage();

    QImage image(w, h, QImage::Format_RGBA8888);
    uint8_t *dst[4] = { image.bits(), nullptr, nullptr, nullptr };
    int dstStride[4] = { image.bytesPerLine(), 0, 0, 0 };
    sws_scale(m_swsCtx, frame->data, frame->linesize, 0, h, dst, dstStride);
    return image;
}

QByteArray VideoPlayer::resampleAudio(AVFrame *frame, int64_t &ptsUs)
{
    ptsUs = -1;
    if (!frame || !m_audioOutput || !m_audioOutput->isActive()) return {};

    AVRational tb = (frame->time_base.num > 0 && frame->time_base.den > 0)
                    ? frame->time_base
                    : m_fmtCtx->streams[m_audioStreamIndex]->time_base;
    ptsUs = ptsToUs(frame->pts, tb);

    int deviceRate = m_audioOutput->sampleRate();
    int deviceCh = m_audioOutput->channelCount();
    if (deviceRate <= 0 || deviceCh <= 0) return {};

    float rate = m_rate.load();
    int outRate = (int)(deviceRate / qBound(0.25f, rate, 8.0f));
    if (outRate < 4000) outRate = 4000;

    AVSampleFormat inFmt = (AVSampleFormat)frame->format;
    int inCh = frame->ch_layout.nb_channels > 0 ? frame->ch_layout.nb_channels : 2;
    int inRate = frame->sample_rate;
    AVSampleFormat outFmt = AV_SAMPLE_FMT_S16;

    // swr 配置变化时重建
    if (!m_swrCtx || m_swrOutRate != outRate || m_swrOutCh != deviceCh ||
        m_swrInRate != inRate || m_swrInCh != inCh || m_swrInFmt != inFmt) {
        if (m_swrCtx) swr_free(&m_swrCtx);
        AVChannelLayout inLayout;
        AVChannelLayout outLayout;
        av_channel_layout_default(&inLayout, inCh);
        av_channel_layout_default(&outLayout, deviceCh);
        int sr = swr_alloc_set_opts2(&m_swrCtx,
            &outLayout, outFmt, outRate,
            &inLayout, inFmt, inRate, 0, nullptr);
        av_channel_layout_uninit(&inLayout);
        av_channel_layout_uninit(&outLayout);
        if (sr < 0 || !m_swrCtx || swr_init(m_swrCtx) < 0) {
            if (m_swrCtx) swr_free(&m_swrCtx);
            return {};
        }
        m_swrOutRate = outRate; m_swrOutCh = deviceCh; m_swrOutFmt = outFmt;
        m_swrInRate = inRate; m_swrInCh = inCh; m_swrInFmt = inFmt;
    }

    int maxOut = swr_get_out_samples(m_swrCtx, frame->nb_samples);
    int bufSize = av_samples_get_buffer_size(nullptr, deviceCh, maxOut, outFmt, 1);
    if (bufSize <= 0) return {};

    QByteArray pcm;
    pcm.resize(bufSize);
    uint8_t *outPtr = (uint8_t*)pcm.data();
    int converted = swr_convert(m_swrCtx, &outPtr, maxOut,
                                (const uint8_t**)frame->data, frame->nb_samples);
    if (converted <= 0) return {};

    int finalSize = av_samples_get_buffer_size(nullptr, deviceCh, converted, outFmt, 1);
    if (finalSize > 0) pcm.resize(finalSize);
    return pcm;
}

void VideoPlayer::waitAVSync(AVFrame *frame)
{
    AVRational tb = (frame->time_base.num > 0 && frame->time_base.den > 0)
                    ? frame->time_base : m_videoTimeBase;
    int64_t ptsUs = ptsToUs(frame->pts, tb);
    if (ptsUs < 0) return;

    m_lastVideoPtsUs = ptsUs;
    emit positionChanged(ptsUs);

    using clock = std::chrono::steady_clock;

    int64_t audioClock = -1;
    if (m_audioOutput && m_audioOutput->isActive()) {
        audioClock = m_audioOutput->clockUs();
    }

    if (audioClock >= 0) {
        // 音频主时钟：视频 PTS 超前音频时钟时等待
        while (m_running.load() == 1 && !m_paused.load()) {
            int64_t ac = m_audioOutput->clockUs();
            if (ac < 0) break;
            int64_t diff = ptsUs - ac;
            if (diff <= 30000) break;  // 30ms 内视为同步
            int64_t chunk = std::min<int64_t>(diff, 20000);
            std::this_thread::sleep_for(std::chrono::microseconds(chunk));
        }
    } else {
        // 无音频：系统时钟
        if (!m_vClockInited) {
            m_vClockInited = true;
            m_vFirstPts = ptsUs;
            m_vClockStart = clock::now();
            return;
        }
        float rate = m_rate.load();
        double targetSec = (ptsUs - m_vFirstPts) / 1e6 / rate;
        double elapsedSec = std::chrono::duration<double>(clock::now() - m_vClockStart).count();
        double delaySec = targetSec - elapsedSec;

        if (delaySec > 0 && delaySec < 2.0) {
            int64_t remainUs = (int64_t)(delaySec * 1e6);
            while (remainUs > 0 && m_running.load() == 1 && !m_paused.load()) {
                int64_t chunk = std::min<int64_t>(remainUs, 20000);
                std::this_thread::sleep_for(std::chrono::microseconds(chunk));
                remainUs -= chunk;
            }
        } else if (delaySec < -2.0) {
            m_vFirstPts = ptsUs;
            m_vClockStart = clock::now();
        }
    }
}

void VideoPlayer::runDemuxer()
{
    emit statusMessage(QString("正在连接: %1").arg(m_url));

    if (!openInputStream()) {
        m_running = 0;
        m_videoQueue->abort();
        m_audioQueue->abort();
        emit finished();
        return;
    }

    emit statusMessage(QString("已连接，开始播放"));

    AVPacket *pkt = av_packet_alloc();

    while (m_running.load() == 1) {
        // 处理 seek 请求
        int64_t seekTarget = m_seekTargetUs.exchange(-1);
        if (seekTarget >= 0 && m_videoStreamIndex >= 0) {
            m_videoQueue->clear();
            m_audioQueue->clear();
            AVRational tb = m_fmtCtx->streams[m_videoStreamIndex]->time_base;
            int64_t targetTs = av_rescale_q(seekTarget, AVRational{1, 1000000}, tb);
            if (av_seek_frame(m_fmtCtx, m_videoStreamIndex, targetTs,
                              AVSEEK_FLAG_BACKWARD) < 0) {
                emit statusMessage("seek 失败");
            }
            m_vClockInited = false;
            if (m_audioOutput) m_audioOutput->resetClock();
            m_flushSeq++;
        }

        int ret = av_read_frame(m_fmtCtx, pkt);
        if (ret < 0) {
            if (ret == AVERROR_EOF) {
                emit statusMessage("播放结束");
            } else {
                char errbuf[256]; av_strerror(ret, errbuf, sizeof(errbuf));
                emit error(QString("读取流失败: %1").arg(QString::fromUtf8(errbuf)));
            }
            break;
        }

        if (pkt->stream_index == m_videoStreamIndex) {
            m_videoQueue->push(av_packet_clone(pkt));
        } else if (pkt->stream_index == m_audioStreamIndex && m_audioStreamIndex >= 0) {
            m_audioQueue->push(av_packet_clone(pkt));
        }
        av_packet_unref(pkt);
    }

    av_packet_free(&pkt);
    if (m_running.load() == 1) {
        m_videoQueue->push(nullptr);
        m_audioQueue->push(nullptr);
    } else {
        m_videoQueue->abort();
        m_audioQueue->abort();
    }
}

void VideoPlayer::runVideoDecoder()
{
    int lastFlush = m_flushSeq.load();
    bool skipUntilKey = false;

    while (m_running.load() == 1) {
        // 暂停时等待
        while (m_paused.load() && m_running.load() == 1) {
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }

        AVPacket *pkt = m_videoQueue->pop();
        if (!pkt) break;

        int curFlush = m_flushSeq.load();
        if (curFlush != lastFlush) {
            avcodec_flush_buffers(m_videoCodecCtx);
            lastFlush = curFlush;
            skipUntilKey = true;
            av_packet_free(&pkt);
            continue;
        }

        bool gotFrame = false;
        AVFrame *frame = decodeVideoPacket(pkt, gotFrame);
        av_packet_free(&pkt);

        if (gotFrame && frame) {
            if (skipUntilKey) {
                if (!(frame->flags & AV_FRAME_FLAG_KEY)) {
                    av_frame_free(&frame);
                    continue;
                }
                skipUntilKey = false;
            }
            waitAVSync(frame);
            if (!m_paused.load()) {
                QImage img = frameToImage(frame);
                if (!img.isNull()) emit newFrame(img);
            }
        }
        if (frame) av_frame_free(&frame);
    }

    // flush remaining
    if (m_videoCodecCtx) {
        avcodec_send_packet(m_videoCodecCtx, nullptr);
        while (m_running.load() == 1) {
            bool gotFrame = false;
            AVFrame *frame = decodeVideoPacket(nullptr, gotFrame);
            if (!gotFrame || !frame) break;
            waitAVSync(frame);
            QImage img = frameToImage(frame);
            if (!img.isNull()) emit newFrame(img);
            av_frame_free(&frame);
        }
    }
}

void VideoPlayer::runAudioDecoder()
{
    int lastFlush = m_flushSeq.load();

    while (m_running.load() == 1) {
        while (m_paused.load() && m_running.load() == 1) {
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }

        AVPacket *pkt = m_audioQueue->pop();
        if (!pkt) break;

        int curFlush = m_flushSeq.load();
        if (curFlush != lastFlush) {
            if (m_audioCodecCtx) avcodec_flush_buffers(m_audioCodecCtx);
            lastFlush = curFlush;
            av_packet_free(&pkt);
            continue;
        }

        bool gotFrame = false;
        AVFrame *frame = decodeAudioPacket(pkt, gotFrame);
        av_packet_free(&pkt);

        if (gotFrame && frame) {
            int64_t ptsUs = -1;
            QByteArray pcm = resampleAudio(frame, ptsUs);
            if (!pcm.isEmpty()) {
                m_audioOutput->pushPCM(pcm, ptsUs);
            }
        }
        if (frame) av_frame_free(&frame);
    }

    // flush remaining audio
    if (m_audioCodecCtx) {
        avcodec_send_packet(m_audioCodecCtx, nullptr);
        while (m_running.load() == 1) {
            bool gotFrame = false;
            AVFrame *frame = decodeAudioPacket(nullptr, gotFrame);
            if (!gotFrame || !frame) break;
            int64_t ptsUs = -1;
            QByteArray pcm = resampleAudio(frame, ptsUs);
            if (!pcm.isEmpty()) m_audioOutput->pushPCM(pcm, ptsUs);
            av_frame_free(&frame);
        }
    }
}

void VideoPlayer::open(const QString &url)
{
    if (m_running.load() == 1) stop();

    m_url = url;
    m_running = 1;
    m_paused = false;
    m_rate = 1.0f;
    m_seekTargetUs = -1;
    m_flushSeq = 0;

    delete m_videoQueue; m_videoQueue = new PacketQueue(512);
    delete m_audioQueue; m_audioQueue = new PacketQueue(512);

    m_demuxerThread = std::thread(&VideoPlayer::runDemuxer, this);
    m_videoThread = std::thread(&VideoPlayer::runVideoDecoder, this);
    m_audioThread = std::thread(&VideoPlayer::runAudioDecoder, this);
}

void VideoPlayer::stop()
{
    m_running = 0;
    m_paused = false;

    if (m_videoQueue) m_videoQueue->abort();
    if (m_audioQueue) m_audioQueue->abort();

    if (m_demuxerThread.joinable()) m_demuxerThread.join();
    if (m_videoThread.joinable()) m_videoThread.join();
    if (m_audioThread.joinable()) m_audioThread.join();

    if (m_videoQueue) { m_videoQueue->clear(); }
    if (m_audioQueue) { m_audioQueue->clear(); }

    if (m_audioOutput) m_audioOutput->stop();

    cleanup();
}

void VideoPlayer::seekTo(int64_t targetUs)
{
    if (m_durationUs.load() > 0) {
        targetUs = qBound<int64_t>(0, targetUs, m_durationUs.load());
    }
    m_seekTargetUs = targetUs;
    m_paused = false;
    if (m_audioOutput) m_audioOutput->resume();
}

void VideoPlayer::seekBy(int64_t deltaUs)
{
    int64_t cur = m_lastVideoPtsUs.load();
    if (cur <= 0 && m_audioOutput) cur = m_audioOutput->clockUs();
    if (cur <= 0) cur = 0;
    seekTo(cur + deltaUs);
}

void VideoPlayer::setPlaybackRate(float rate)
{
    rate = qBound(0.25f, rate, 8.0f);
    // 先通知 AudioOutput rebase 时钟（用旧 rate 算当前位置，避免跳变）
    if (m_audioOutput) m_audioOutput->setRate(rate);
    m_rate = rate;
    // 无音频流时用系统时钟，需要重置基准
    if (m_vClockInited) {
        m_vClockInited = false;
    }
}

void VideoPlayer::setPause(bool paused)
{
    m_paused = paused;
    if (m_audioOutput) {
        if (paused) m_audioOutput->suspend();
        else m_audioOutput->resume();
    }
}

void VideoPlayer::setVolume(float v)
{
    if (m_audioOutput) m_audioOutput->setVolume(v);
}

void VideoPlayer::setMuted(bool m)
{
    if (m_audioOutput) m_audioOutput->setMuted(m);
}

int64_t VideoPlayer::currentPosUs() const
{
    if (m_audioOutput && m_audioOutput->isActive()) {
        int64_t ac = m_audioOutput->clockUs();
        if (ac >= 0) return ac;
    }
    return m_lastVideoPtsUs.load();
}

void VideoPlayer::cleanup()
{
    if (m_swsCtx) { sws_freeContext(m_swsCtx); m_swsCtx = nullptr; }
    m_swsW = m_swsH = 0; m_swsFmt = AV_PIX_FMT_NONE;

    if (m_swrCtx) { swr_free(&m_swrCtx); }
    m_swrOutRate = m_swrOutCh = 0; m_swrOutFmt = AV_SAMPLE_FMT_NONE;
    m_swrInRate = m_swrInCh = 0; m_swrInFmt = AV_SAMPLE_FMT_NONE;

    m_vClockInited = false;
    m_vFirstPts = 0;
    m_videoTimeBase = AVRational{0, 1};

    if (m_videoCodecCtx) {
        if (m_videoCodecCtx->hw_device_ctx) av_buffer_unref(&m_videoCodecCtx->hw_device_ctx);
        avcodec_free_context(&m_videoCodecCtx);
    }
    if (m_audioCodecCtx) avcodec_free_context(&m_audioCodecCtx);
    if (m_hwDeviceCtx) av_buffer_unref(&m_hwDeviceCtx);
    if (m_fmtCtx) avformat_close_input(&m_fmtCtx);

    m_hwEnabled = false;
    m_hwDecoderName = "software";
    m_videoStreamIndex = -1;
    m_audioStreamIndex = -1;
    m_durationUs = 0;
    m_lastVideoPtsUs = 0;
}
