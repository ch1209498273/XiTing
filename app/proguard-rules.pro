# 息屏听剧 R8 混淆规则
# 项目无反射、无运行时类查找、无跨进程序列化框架；
# XML 布局引用的自定义 View（PetView 等）由 AAPT 自动生成 keep 规则。
# 以下仅保留崩溃行号，便于 logcat 排障（无上报SDK，映射表仅本机使用）。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
