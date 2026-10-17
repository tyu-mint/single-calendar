# 单向历Android桌面小组件

适用于Android的单向历桌面小组件。

## 功能

- 显示每日单向历图片，按北京时间自动更新
- 每天00:05自动刷新
- 点击小组件可手动刷新
- 网络失败时保留上一次成功图片
- 默认桌面占位尺寸为2×3，可调整大小

图片来源：`https://img.owspace.com/Public/uploads/Download/YYYY/MMDD.jpg`

## 构建

JDK 17, Android SDK, Gradle 8.9

```bash
gradle --no-daemon assembleDebug
```
