# 快览 QuickLook

快览是一款轻量级 Android 文件浏览器与快速查看器，支持浏览本地存储、常用目录与 SAF 目录，并内置图片、视频、音频、文本与压缩包五种查看器。

## 功能特性

- 文件浏览：存储 / 最近 / 常用目录（下载、相机、图片、音乐、视频、文档）与书签
- 文件管理：新建、重命名、删除、分享、跳转目录、全选 / 反选
- 搜索与排序：关键字 / 正则搜索，名称、修改时间、大小、类型排序，文件夹优先
- SAF 目录：通过系统文件选择器添加访问外部目录
- 快速查看器：
  - 图片查看器
  - 视频播放器（Media3 / ExoPlayer）
  - 音频播放器（波形可视化）
  - 文本编辑器（查看与编辑）
  - 压缩包查看器（ZIP / 7z 等，支持加密条目）
- 主题：动态取色（Android 12+）、浅色 / 深色 / 跟随系统
- 崩溃捕捉：未捕获异常自动记录并展示崩溃详情页，支持复制日志与一键重启
- 外部打开：注册为图片 / 视频 / 音频 / 文本文件的系统打开方式

## 技术栈

- Kotlin + Jetpack Compose（Material 3，动态取色）
- Navigation Compose、DataStore、DocumentFile
- Coil（图片加载）、Media3 / ExoPlayer（播放）
- Zip4j、7-Zip-JBinding（压缩包）、Amplituda（音频波形）

## 参考项目

- [柠檬音乐](https://github.com/yangSpica27/SPICaMusic_Android)：参考实现音乐播放器（音频播放与波形可视化）
- [质感文件 (Material Files)](https://github.com/zhanghai/MaterialFiles)：参考实现文件浏览与管理交互

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

## 作者

FuYue-WanJie
