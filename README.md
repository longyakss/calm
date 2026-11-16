# Calm

> 安卓全能型混合工具箱 —— 浏览器 · 下载器 · 文件管理 · 播放器 · 阅读器，一个 App 五大闭环。

![版本](https://img.shields.io/badge/version-0.6.0-blue) ![Android](https://img.shields.io/badge/Android-7.0%2B-green) ![Kotlin](https://img.shields.io/badge/Kotlin-Compose-orange) ![License](https://img.shields.io/badge/用途-学习研究-important)

## 📥 下载安装

- **[下载 v0.6.0 APK（3.0MB）](https://github.com/longyakss/calm/releases/download/v0.6.0/app-release.apk)**
- 或前往 [Releases 页面](https://github.com/longyakss/calm/releases/latest) 获取最新版本
- 要求 Android 7.0+；安装时允许「未知来源」；文件管理等功能需授予「所有文件访问」权限

## ✨ 功能总览

### 🌐 浏览器
- **多标签浏览**（上限 10，独立 WebView 复用，返回键智能后退）
- **智能搜索栏**：网址自动识别、剪贴板链接检测、6 大搜索引擎一键切换（百度/Google/必应/搜狗/360/DuckDuckGo）
- **资源嗅探**：自动拦截页面图片/音频/视频/下载链接，四分类面板，一键下载
- **广告拦截**：内置 60+ 广告与追踪域名规则，菜单开关，实时计数
- **阅读模式**：一键提取网页正文，净化排版
- **夜间模式**：页面反色，独立于系统深色模式
- 书签 / 历史记录、主页快速访问、下载确认弹窗（线程数/重命名/复制链接）

### ⬇️ 下载器
- **多线程分段下载**（1-16 线程 Range 并发）
- **断点续传**（分段进度落盘，重启 App 继续传）
- 后台前台服务保活 + 常驻通知，息屏不掉速
- 按类型自动分类存储（Pictures/Videos/Music/Documents/Archives）
- 暂停 / 恢复 / 删除（可选删文件）、失败重试

### 📁 文件管理器
- 全盘浏览：列表/排序/类型筛选/隐藏文件
- 文件操作：复制 / 移动 / 重命名 / 删除 / 分享（带进度）
- **内置查看器**：图片（缩放平移）、文本（GBK/UTF-8 自动识别）、PDF（逐页渲染）
- 视频/音频调用内置播放器

### 🎬 播放器
- 基于 Media3 ExoPlayer，注册为系统视频/音频处理器
- **后台播放**：熄屏持续，系统播控中心/通知栏媒体卡片可操作
- **手势分区**：左 1/3 调亮度 · 右 1/3 调音量 · 中间调进度，边缘指示器实时显示
- 双击两侧 ±10 秒 / 双击中间暂停，倍速 0.5x-3x

### 📖 阅读器（书架）
- **格式支持**：txt（编码自动识别）/ epub
- 章节自动识别（无标记按字数切节），目录快速跳转
- 排版调节：字号 12-32sp / 行距 / 段距 / 首行缩进，全局设置独立存储
- **阅读进度**：自动保存与恢复，异常退出不丢
- **TTS 听书**：系统语音引擎朗读，章节自动续读
- **净化规则**：文本/正则替换（仅显示层生效，不改原文件）

### ⚙️ 其他
- 8 套主题色 + 跟随系统/浅色/深色外观模式
- **防杀设置**：电池白名单/通知/无障碍状态检测（红黄绿），按品牌给出后台保活引导路径
- Material Design 3 设计语言

## 🗺️ 开发路线

- [x] v0.1.0 工程骨架 + 主题系统
- [x] v0.5.0 五大模块主功能
- [x] v0.6.0 广告拦截 + 阅读模式 + 防杀设置
- [ ] 翻译模块（在线 + 本地选装）
- [ ] 密码管理器（AES-256-GCM + Keystore）
- [ ] 皮肤主题库、视频弹窗小窗、umd/mobi/azw3 格式
- [ ] FFmpeg 转码合并、yt-dlp 集成

## 🔨 构建

```bash
git clone https://github.com/longyakss/calm.git
cd calm
gradle assembleRelease
```

或直接 push `main` 分支，GitHub Actions 自动构建（产物见 Actions → calm-apk）。

本地签名：复制 `keystore.properties.example` 为 `keystore.properties` 并填入自己的签名信息。

## 🧱 技术栈

Kotlin · Jetpack Compose · MVVM + Clean Architecture · Hilt · Room · DataStore · OkHttp · Media3 ExoPlayer · Jsoup · Coil · WebView

## ⚠️ 免责声明

1. 本软件仅供技术研究与学习使用。
2. 使用本软件下载/解析的任何内容，版权责任由使用者自行承担。
3. 本软件不对任何因使用导致的直接或间接损失负责。请尊重版权，支持正版。
4. 请勿使用本软件从事任何违法活动。
