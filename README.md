# 单向历 Android 桌面小组件

适用于 Android / 小米 HyperOS 的单向历桌面小组件。

## 功能

- 显示每日单向历图片，按北京时间自动更新
- 每天 00:05 自动刷新
- 点击小组件可手动刷新
- 网络失败时保留上一次成功图片
- 默认桌面占位尺寸为 2 × 3，可调整大小

图片来源：`https://img.owspace.com/Public/uploads/Download/YYYY/MMDD.jpg`

## 构建

需要 JDK 17、Android SDK，以及 Gradle 8.9。

```bash
gradle --no-daemon assembleDebug
```

## GitHub Actions 自动构建

推送到 `main` 后会自动构建并发布 APK Release。为了让后续 APK 能直接覆盖安装，需要在仓库的 **Settings → Secrets and variables → Actions** 中配置以下四个 Secrets：

- `SIGNING_KEYSTORE_BASE64`：固定签名 keystore 文件的 Base64 内容
- `SIGNING_KEYSTORE_PASSWORD`：keystore 密码
- `SIGNING_KEY_ALIAS`：签名别名
- `SIGNING_KEY_PASSWORD`：签名密钥密码

首次使用时必须使用同一份 keystore。若没有配置这些 Secrets，签名发布流程会失败。
