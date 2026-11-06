# 企业微信去水印

[![Build](https://github.com/OWNER/REPO/actions/workflows/build.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/build.yml)

一个 Xposed 模块，用于移除企业微信（WeCom）**聊天界面**与**通讯录**上的水印，
并同时屏蔽日历、邮件、文件预览等页面的全屏水印。

只改变本机屏幕上的显示效果，不修改应用安装包、不联网、不上传任何数据。

---

## 安装

### 前置条件

- 设备已 root，并装好 **LSPosed**（或 EdXposed）
- 企业微信 **4.x**
- Android 8.0 及以上

### 步骤

1. 从 [Releases](../../releases) 下载 APK（也可按下方「构建」自行编译）
2. 安装 APK：手机上直接打开安装，或执行 adb install -r app-debug.apk
3. 打开 **LSPosed** → 模块 → 启用「企业微信去水印」
4. 点进该模块 → **作用域** → 勾选「企业微信」
5. 强制停止企业微信（系统设置 → 应用 → 企业微信 → 强行停止），再重新打开
6. 进入任意聊天页和通讯录，水印应当已经消失

### 没生效怎么办

- 确认 LSPosed 里模块处于**启用**状态，且作用域已勾选「企业微信」
- 确认操作后**强制停止**过企业微信（仅退出后台不够）
- 如果用的是手机分身 / 工作资料 / 多用户空间，需要在**同一个空间内**安装并启用模块
- 确认企业微信版本为 4.x；大版本升级后模块可能需要更新
- 启动时若弹出「N 项 hook 失败」的提示，说明模块没能完全接入当前版本，可把提示内容提交到 Issues

---

## 说明

本模块只影响**屏幕上显示的水印**。

- 保存到相册的图片、导出的文件，如果本身被写入了水印，不会被去除
- 文档正文、邮件正文里的水印由服务端生成，不在处理范围内
- 会议模块的水印属于另一套体系，未覆盖

如果后续版本企业微信调整了实现方式，或对运行环境做完整性检测，模块可能失效。

---

## 构建

要求：JDK 17 或 21、Android SDK Platform 34。

**Android Studio**：直接 Open 本工程，等 Gradle 同步完成后 Build → Build APK(s)。

**命令行**：

```bash
# 在工程根目录创建 local.properties，写入 SDK 路径
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 需要 Gradle 8.7 及以上
gradle assembleDebug
```

产物位于 app/build/outputs/apk/debug/app-debug.apk。

---

## 合规提示

企业水印通常是企业管理员为**数据防泄漏与泄露溯源**强制开启的策略。
在个人设备上关闭它只是本机显示层面的改动，但可能违反所在公司的信息安全规定；
分享出去的截图或录屏如果缺少水印，责任由使用者承担。请自行确认使用场景是否合规。
