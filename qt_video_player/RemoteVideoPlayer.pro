#-------------------------------------------------
# 远程视频播放器 - Qt 5.12.12 + FFmpeg 硬件解码
# 特性：D3D11VA/DXVA2 硬件解码、多线程解封装/解码、OpenGL渲染
#-------------------------------------------------

QT += core gui widgets opengl multimedia

CONFIG += c++14

TARGET = RemoteVideoPlayer
TEMPLATE = app

# 禁止在源码目录生成临时文件，统一输出到 build 目录
OBJECTS_DIR = build/obj
MOC_DIR = build/moc
RCC_DIR = build/rcc
UI_DIR = build/ui
DESTDIR = build/bin

# FFmpeg 开发库路径（包含 include / bin / lib）
FFMPEG_DIR = C:/Users/king/source/repos/WinFileTransfer/ffmpeg

INCLUDEPATH += $$FFMPEG_DIR/include

LIBS += -L$$FFMPEG_DIR/lib \
        -lavcodec -lavformat -lavutil -lswscale -lswresample -lavdevice

# Windows 平台附加依赖（硬件解码 D3D11VA 需要 + OpenGL 固定管线）
win32:LIBS += -ld3d11 -ldxva2 -ld3d9 -lopengl32

SOURCES += \
    main.cpp \
    mainwindow.cpp \
    videoplayer.cpp \
    videorenderer.cpp \
    audiooutput.cpp

HEADERS += \
    mainwindow.h \
    videoplayer.h \
    videorenderer.h \
    audiooutput.h
