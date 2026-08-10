# 快览 QuickLook

快览是一款轻量级 Android 文件浏览器与快速查看器，支持浏览本地存储、常用目录与 SAF 目录，并内置图片、视频、音频、文本与压缩包五种查看器。支持 Shizuku 授权访问 /sdcard/ 目录。

## 功能特性

- 文件浏览：存储 / 最近 / 常用目录（下载、相机、图片、音乐、视频、文档）与书签
- 文件管理：新建、重命名、删除、分享、跳转目录、全选 / 反选
- 搜索与排序：关键字 / 正则搜索，名称、修改时间、大小、类型排序，文件夹优先
- SAF 目录：通过系统文件选择器添加访问外部目录
- Shizuku 授权：通过 Shizuku 授权后可访问 /sdcard/ 系统目录，无需授予 MANAGE_EXTERNAL_STORAGE 权限
- 快速查看器：
  - 图片查看器
  - 视频播放器（Media3 / ExoPlayer）
  - 音频播放器（波形可视化）
  - 文本编辑器（查看与编辑，支持语法高亮与行号的代码编辑体验）
  - 压缩包查看器（ZIP / 7z 等，支持加密条目）
- 主题：动态取色（Android 12+）、浅色 / 深色 / 跟随系统
- 崩溃捕捉：未捕获异常自动记录并展示崩溃详情页，支持复制日志与一键重启
- 外部打开：注册为图片 / 视频 / 音频 / 文本文件的系统打开方式

## 技术栈

- Kotlin + Jetpack Compose（Material 3，动态取色）
- Navigation Compose、DataStore、DocumentFile
- Coil（图片加载）、Media3 / ExoPlayer（播放）
- Zip4j、7-Zip-JBinding（压缩包）、Amplituda（音频波形）
- Shizuku（可选授权访问系统目录）

## Shizuku 使用说明

Shizuku 是一种无需 Root 即可通过 ADB 或系统应用获取系统级权限的技术。

### 前置条件

1. 安装 [Shizuku](https://shizuku.rikka.app/) 应用
2. 通过以下方式之一启动 Shizuku：
   - **无线调试**：在 Shizuku 应用中点击「通过无线调试启动」
   - **ADB**：连接电脑并执行 `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/files/shizuku.sh`

### 在快览中使用

1. 确保 Shizuku 已启动
2. 打开快览 → 设置 → Shizuku 授权
3. 点击「授权」按钮，在弹出的对话框中确认授权
4. 授权成功后，存储标签页将显示「Shizuku 存储」入口，可浏览 /sdcard/ 目录

### 功能说明

- 授权后可访问完整的 /sdcard/ 目录结构
- 支持文件浏览、新建、重命名、删除等操作
- 书签功能同样适用于 Shizuku 目录
- 授权失效时需重新授权

## 参考项目

- [柠檬音乐](https://github.com/yangSpica27/SPICaMusic_Android)：参考实现音乐播放器（音频播放与波形可视化）
- [质感文件 (Material Files)](https://github.com/zhanghai/MaterialFiles)：参考实现文件浏览与管理交互
- [烛文件 (ZhuFiler)](https://github.com/Artzhu86/ZhuFiler)：参考实现文件管理

## 构建

环境要求：

- JDK 17
- Android SDK Platform 35、Build Tools 35.0.0

```bash
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/`。

## 版本

- 版本名：2.0
- 版本号：20260805

## 交流反馈

当前为重构版本，功能尚未完善、稳定性有限，欢迎反馈问题与建议：

- QQ 交流群：980108235
- GitHub Issue / Pull Request：<https://github.com/FuYue-WanJie/quicklook>
- 邮箱：kittenfeelfish@hotmail.com、wanjiestudio@163.com、linx20770@gmail.com
- 作者在 B 站、抖音、快手、Solar Network、Telegram 等平台均有账号，可搜索「符跃-万界」「符跃 万界」「FuYue-WanJie」「FuYue_WanJie」找到我
- 提示：本人在 X（Twitter）上的账号刚注册就被封了，无法通过 X 联系；其他平台搜不到，那就是真的还没注册或我记错了

## 作者

FuYue-WanJie
