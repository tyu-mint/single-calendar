# 单向历 Android 桌面小组件

把「单向历」每日图片放到 Android / HyperOS 桌面上。轻量、无多余卡片背景，适合做成桌面日历。

## 功能

- **每日自动更新**：按北京时间（UTC+8）获取当天的单向历图片，每天约 00:05 刷新。
- **手动刷新**：打开应用后点击「刷新今日单向历」，也可以点击桌面小组件触发刷新。
- **缓存与失败保护**：图片保存在应用本地；网络不可用或当天图片获取失败时，保留上次成功获取的图片。
- **适配浅色 / 深色模式**：根据系统主题切换原图或反色版本，并定期检查主题变化。
- **桌面小组件**：透明背景、图片居中完整显示，支持调整小组件大小；图片使用圆角处理。
- **查看大图**：点击小组件图片进入大图页面，长按图片可保存到相册、分享图片，或进入系统应用信息页卸载应用。
- **隐藏应用图标**：可以隐藏启动器中的应用图标；小组件仍可使用，之后可通过小组件底部的隐藏点击区域重新打开设置。

## 小米澎湃 OS / MIUI 小部件

此分支已加入小米小部件的代码级适配：

- 在应用清单中声明 `miuiWidget` 和应用级 `miuiWidgetVersion`。
- 接收小米桌面的 `miui.appwidget.action.APPWIDGET_UPDATE` 展现刷新广播。
- 将小组件接收器放入 `:widgetProvider` 独立进程。
- 按小米文档建议，将最小尺寸配置为 4×4（300dp × 250dp），同时保留可调整尺寸的能力。

小米官方说明：[小部件技术规范与系统能力](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1584)。

**审核说明：** 代码接入后，普通 Android 桌面小组件仍按原生规范工作。要让组件正式收录到澎湃 OS 的「小部件中心」，还需要按小米官方流程提交应用和小组件审核；本项目尚未完成该审核。

## ## 安装

前往 [GitHub Releases](https://github.com/tyu-mint/single-calendar/releases/latest) 下载最新的 `SingleCalendarWidget.apk` 并安装。

如果安装时提示风险或要求允许安装未知来源应用，请确认 APK 来源为本项目的 GitHub Releases 后，再按系统提示操作。

## 添加桌面小组件

1. 安装并打开一次「单向历」。
2. 长按桌面空白处，进入「小组件」列表。
3. 找到「单向历」，将小组件添加到桌面。
4. 按桌面布局需要调整尺寸。若图片显示尚未更新，可打开应用手动刷新。

不同 Android 启动器对小组件的尺寸、留白和裁切方式可能略有不同。

## 图片来源与更新机制

每日图片来自单向空间公开图片地址，按北京时间日期拼接：

```text
https://img.owspace.com/Public/uploads/Download/YYYY/MMDD.jpg
```

应用会下载当天图片并保存到内部缓存，同时生成用于深色模式显示的反色图片。应用需要网络连接才能获取最新图片；缓存图片可在获取失败时继续显示。

## 从源码构建

### 环境要求

- JDK 17
- Android SDK（compileSdk 35）
- Gradle 8.9

在仓库根目录执行：

```bash
gradle --no-daemon assembleDebug
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

构建正式版：

```bash
gradle --no-daemon assembleRelease
```

本地构建 Release 需要配置签名环境变量：`SIGNING_KEYSTORE_FILE`、`SIGNING_KEYSTORE_PASSWORD`、`SIGNING_KEY_ALIAS` 和 `SIGNING_KEY_PASSWORD`。缺少签名配置时，正式版无法按项目发布流程完成签名。

## 自动构建与发布

仓库配置了 GitHub Actions：

- 推送到 `feature/**` 分支并修改应用或构建文件时，自动构建 Debug 预览 APK，供安装测试；预览包使用独立的 `com.icetea.singlecalendar.preview` 包名，可与正式版并存。
- 推送到 `main` 分支且修改 `app/**`、Gradle 配置或构建工作流时，才会构建签名 Release APK 并发布 GitHub Release。
- 预览 APK 和正式 APK 都会作为 GitHub Actions Artifact 上传；预览产物保留 14 天，正式产物保留 30 天。
- 正式 Release 使用固定签名，便于后续版本直接覆盖更新。自动发布需要在仓库 Actions Secrets 中正确配置签名相关密钥。

## 许可证

本项目源码采用 [MIT License](LICENSE) 开源。你可以使用、复制、修改、合并、发布、分发、再许可及销售软件副本，但需要在副本或重要部分中保留原版权声明和许可证文本。软件按“现状”提供，不附带明示或默示担保。

MIT 许可证仅适用于本项目作者拥有版权的代码及相关文件；每日单向历图片由单向空间提供，图片版权及使用权限不因本项目开源而改变。第三方依赖也各自遵循其原有许可证。

## 项目信息

- 应用名称：单向历
- 包名：`com.icetea.singlecalendar`
- 最低 Android 版本：Android 6.0（API 23）
- 主要实现：Java + Android App Widget
- 开源许可证：[MIT](LICENSE)
