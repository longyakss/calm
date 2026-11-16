# Calm

安卓全能型混合工具箱（浏览器 + 下载器 + 文件管理 + 播放器 + 阅读器）。

- 产品设计方案：`../Calm-完整产品设计方案.html`（v5.4，264 项功能）
- 开发方案：`../Calm-开发方案.md`（Phase 计划 / 验收标准 / 项目档案）
- 2026-11-16 启动开发，Phase 1 基础框架进行中

## 构建

push `main` 自动触发 GitHub Actions，产物 artifact：`calm-apk`。
本地无需 Android SDK，全部依赖 CI 构建。

## 签名（密钥不入库，CI 通过 Secrets 注入；严禁删除/更换/改密码）

- keystore：仓库外本地保存；CI 构建时由 GitHub Actions Secret `CALM_KEYSTORE_BASE64` 解码生成
- 密码：存于 Actions Secrets（`CALM_STORE_PASSWORD` / `CALM_KEY_PASSWORD` / `CALM_KEY_ALIAS`）
- 本地开发：将 `release.keystore` 放回仓库根目录即可（已被 .gitignore 忽略，不会提交）

## 版本规则

- `versionCode`：每次出包 +1
- `versionName`：按里程碑递增（M1=0.1.0 → M8=1.0.0），bug +0.0.1 / 功能 +0.1

## 模块

| 模块 | 职责 | 启用阶段 |
|---|---|---|
| `:app` | 壳工程，导航与页面组装 | Phase 1 |
| `:core:common` | 通用工具 / 主题 / 组件库 | Phase 1 |
| `:core:settings` | 设置中心 / 防杀 / 主题 | Phase 1（骨架） |
| `:library:webview` `:core:browser` | 浏览器 / 嗅探 | Phase 2 |
| `:core:downloader` | 下载引擎 | Phase 3 |
| `:core:player` `:library:ffmpeg` | 播放器 / FFmpeg | Phase 4 |
| `:core:filemanager` | 文件管理 | Phase 5 |
| `:core:reader` | 书架 / 阅读器 / TTS | Phase 6 |
| `:core:translate` `:core:privacy` | 翻译 / 隐私 | Phase 7 |
