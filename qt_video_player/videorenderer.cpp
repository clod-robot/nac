#include "videorenderer.h"

VideoRenderer::VideoRenderer(QWidget *parent)
    : QOpenGLWidget(parent)
{
    setAutoFillBackground(false);
}

VideoRenderer::~VideoRenderer()
{
    if (m_useOpenGL) {
        makeCurrent();
        if (m_texture) {
            delete m_texture;
            m_texture = nullptr;
        }
        doneCurrent();
    }
}

void VideoRenderer::updateFrame(const QImage &image)
{
    QMutexLocker locker(&m_frameMutex);
    m_latestFrame = image;
    m_frameUpdated = true;
    locker.unlock();
    update();
}

void VideoRenderer::initializeGL()
{
    // Qt 5.12: initializeOpenGLFunctions() 返回 void
    // 上下文在 initializeGL 调用时已由 QOpenGLWidget 创建
    initializeOpenGLFunctions();
    m_useOpenGL = (context() != nullptr && context()->isValid());
    if (m_useOpenGL) {
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    }
}

void VideoRenderer::resizeGL(int w, int h)
{
    m_viewportW = w;
    m_viewportH = h;
    if (m_useOpenGL) {
        glViewport(0, 0, w, h);
    }
}

void VideoRenderer::paintGL()
{
    QImage frame;
    {
        QMutexLocker locker(&m_frameMutex);
        if (!m_frameUpdated && m_latestFrame.isNull()) return;
        frame = m_latestFrame;
        m_frameUpdated = false;
    }
    if (frame.isNull()) return;

    // ---- QPainter 回退：无 OpenGL 时用 CPU 绘图 ----
    if (!m_useOpenGL) {
        QPainter painter(this);
        painter.fillRect(rect(), Qt::black);
        QImage scaled = frame.scaled(width(), height(),
                                     Qt::KeepAspectRatio,
                                     Qt::SmoothTransformation);
        int x = (width() - scaled.width()) / 2;
        int y = (height() - scaled.height()) / 2;
        painter.drawImage(x, y, scaled);
        return;
    }

    // ---- OpenGL 渲染路径 ----
    glClear(GL_COLOR_BUFFER_BIT);

    if (!m_texture || m_texture->width() != frame.width() ||
        m_texture->height() != frame.height()) {
        if (m_texture) {
            delete m_texture;
            m_texture = nullptr;
        }
        m_texture = new QOpenGLTexture(frame);
        m_texture->setMinificationFilter(QOpenGLTexture::Linear);
        m_texture->setMagnificationFilter(QOpenGLTexture::Linear);
        m_texture->setWrapMode(QOpenGLTexture::ClampToEdge);
    } else {
        m_texture->setData(frame);
    }

    float imgAspect = (float)frame.width() / frame.height();
    float viewAspect = (float)m_viewportW / m_viewportH;

    float drawW, drawH;
    if (imgAspect > viewAspect) {
        drawW = (float)m_viewportW;
        drawH = drawW / imgAspect;
    } else {
        drawH = (float)m_viewportH;
        drawW = drawH * imgAspect;
    }

    float x = (m_viewportW - drawW) / 2.0f;
    float y = (m_viewportH - drawH) / 2.0f;

    float left   = (x / m_viewportW) * 2.0f - 1.0f;
    float right  = ((x + drawW) / m_viewportW) * 2.0f - 1.0f;
    float bottom = (y / m_viewportH) * 2.0f - 1.0f;
    float top    = ((y + drawH) / m_viewportH) * 2.0f - 1.0f;

    m_texture->bind();
    glEnable(GL_TEXTURE_2D);
    glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(left, bottom);
        glTexCoord2f(1, 1); glVertex2f(right, bottom);
        glTexCoord2f(1, 0); glVertex2f(right, top);
        glTexCoord2f(0, 0); glVertex2f(left, top);
    glEnd();
    m_texture->release();
}
