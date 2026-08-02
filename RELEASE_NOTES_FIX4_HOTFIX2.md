# MD3-FIX4-HOTFIX2: 设置页「看不到新增语言选项」修复

## 用户反馈
安装 v1.17.0028-FIX3-HOTFIX 后，设置页**能正常打开**（闪退修复了），但**完全看不到「语言设置」区域**，截图里只有「视觉效果 + 预览」。

## 根因（两个叠加问题）
### 1. versionCode 永远是 1 —— 覆盖安装不解压新资源！
```
package: name='com.valvesoftware.source'
  versionCode='1'        ← 致命！
  versionName='1.17'     ← 从没变过！
```
Android 在覆盖安装同包名 APK 时：
- **如果 `versionCode` 没有变大**，部分系统/厂商 ROM（尤其是 MIUI、ColorOS、早期 Android）会**跳过资源解压**，直接复用缓存里的 `resources.arsc` / `res/layout/`；
- 所以即使 APK 里已经打包了新版 `activity_settings.xml`（带语言区），运行时加载的依然是旧版 `activity_settings.xml`（只有深色主题 + 动态取色 + 主题色 + 预览，没有语言区）；
- 这也解释了为什么 `aapt dump` 能看到语言区，但用户手机上完全看不到。

### 2. 缺少构建前清理，aapt 增量 package 偶尔缓存脏资源
FIX3 构建时没有清 `bin/`，`aapt package -f` 在部分情况下不会重写整个 `resources.arsc`。

## 修复
### 1) AndroidManifest.xml — 强制升级版本号
```xml
versionCode="1170030"   ← 1 → 1170030（保证以后每次release都加）
versionName="1.17.30"   ← 1.17 → 1.17.30
```
规则：`versionCode = 1170000 + build`，保证以后 release 单调递增。

### 2) 强制完整清理构建
```
rm -rf bin/ gen/
bash manual-build.sh  ← 67 classes, 0 errors
```

### 3) 安装提示
> 安装 HOTFIX2 前，请**先卸载旧版**（或清除应用数据），再安装 1.17.30。
> 即使 versionCode 升级了，部分机型仍可能因签名 debug→debug 覆盖时资源缓存不刷新。

## 验证
- `aapt dump badging`: **versionCode=1170030, versionName=1.17.30** ✓
- activity_settings.xml RadioButton 总数: **7**（语言区4 + 深色模式3）✓
- md3_ui_lang_system / zh_cn / zh_tw / en 4 个 id 全部存在 ✓
