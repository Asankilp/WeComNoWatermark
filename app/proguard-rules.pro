# Xposed API 由框架在运行期提供，不需要打包
-dontwarn de.robv.android.xposed.**
-keep class de.robv.android.xposed.** { *; }

# 入口类必须保留
-keep class moe.erroro.wecomnowatermark.** { *; }
