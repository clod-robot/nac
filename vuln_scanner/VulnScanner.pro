#-------------------------------------------------
# 服务器与网络设备漏洞扫描工具
# Qt 5.12.12 (MinGW 7.3 64bit)
# 仅依赖 Qt Network：TCP 连接扫描 / Banner 识别 / 规则化 CVE 匹配
# 仅限对已获书面授权的目标使用
#-------------------------------------------------

QT += core gui widgets network

CONFIG += c++14

TARGET = VulnScanner
TEMPLATE = app

OBJECTS_DIR = build/obj
MOC_DIR = build/moc
RCC_DIR = build/rcc
UI_DIR = build/ui
DESTDIR = build/bin

SOURCES += \
    main.cpp \
    mainwindow.cpp \
    core/scanengine.cpp \
    core/porttask.cpp \
    core/targetparser.cpp \
    core/servicedb.cpp \
    vuln/vulndb.cpp \
    report/reportexport.cpp

HEADERS += \
    mainwindow.h \
    model/scantypes.h \
    core/scanengine.h \
    core/porttask.h \
    core/targetparser.h \
    core/servicedb.h \
    vuln/vulndb.h \
    report/reportexport.h

RESOURCES += resources/app.qrc
