#include <QApplication>
#include "mainwindow.h"

int main(int argc, char *argv[])
{
    // Qt 5.12 默认使用 Angle（OpenGL ES），为获得更好的桌面 OpenGL 性能
    // 可通过环境变量强制使用桌面 OpenGL（硬件解码 + GPU 渲染更流畅）
    QApplication::setAttribute(Qt::AA_UseDesktopOpenGL);

    QApplication app(argc, argv);
    app.setApplicationName("RemoteVideoPlayer");

    MainWindow w;
    w.show();

    // 支持命令行传入 URL 直接播放：RemoteVideoPlayer.exe http://.../test.m3u8
    const QStringList args = QApplication::arguments();
    if (args.size() > 1) {
        w.playUrl(args.at(1));
    }

    return app.exec();
}
