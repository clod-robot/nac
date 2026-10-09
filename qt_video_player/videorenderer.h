#ifndef VIDEORENDERER_H
#define VIDEORENDERER_H

#include <QOpenGLWidget>
#include <QOpenGLFunctions>
#include <QOpenGLTexture>
#include <QImage>
#include <QMutex>
#include <QSize>
#include <QPainter>

/**
 * @brief 视频渲染控件（OpenGL 优先，QPainter 回退）
 *
 * 渲染策略：
 *   - OpenGL 可用时：QOpenGLTexture 上传 RGBA → 固定管线四边形绘制（GPU 渲染）
 *   - OpenGL 不可用：QPainter 缩放绘制（CPU 渲染，兼容无 GPU 驱动的系统）
 *
 * 兼容性：Win7-11 桌面 OpenGL 几乎总是可用；XP 软件渲染降级
 */
class VideoRenderer : public QOpenGLWidget, protected QOpenGLFunctions
{
    Q_OBJECT

public:
    explicit VideoRenderer(QWidget *parent = nullptr);
    ~VideoRenderer() override;

    QSize sizeHint() const override { return QSize(1280, 720); }

public slots:
    void updateFrame(const QImage &image);

protected:
    void initializeGL() override;
    void resizeGL(int w, int h) override;
    void paintGL() override;

private:
    QOpenGLTexture *m_texture = nullptr;
    QImage m_latestFrame;
    QMutex m_frameMutex;
    bool m_frameUpdated = false;
    int m_viewportW = 0;
    int m_viewportH = 0;
    bool m_useOpenGL = false;  // initializeOpenGLFunctions() 成功后设为 true
};

#endif // VIDEORENDERER_H
