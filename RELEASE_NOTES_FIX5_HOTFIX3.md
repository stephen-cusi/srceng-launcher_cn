# MD3-FIX5-HOTFIX3: 真正修复「看不到语言设置」

## 用户反馈
安装 **1.17.30**（versionCode=1170030，确实升级成功，应用信息里显示版本 1.17.30）后，设置页依然完全没有「语言设置」区域，截图和之前一模一样。

## 根因（这一次是真的！）
### 致命错误：`res/layout-v21/activity_settings.xml` 根本没被修改

Android 资源加载顺序：
- **SDK_INT >= 21（Android 5.0+，占现在用户的 99.9%）**：
  → 加载 `res/layout-v21/activity_settings.xml`（如果存在）
  → **只有当 layout-v21 不存在时**，才 fallback 到默认的 `res/layout/activity_settings.xml`

我之前**只改了 `res/layout/activity_settings.xml`**（加了语言区、改了 @+id），但 `res/layout-v21/activity_settings.xml` 这个文件**依然是最初的旧版**：
```
layout-v21 旧版 RadioButton 数：3   ← 只有深色模式的 跟随系统/关闭/开启
layout    新版 RadioButton 数：7   ← 语言区 4 + 深色 3
```
结果：用户手机（必是 API 21+）加载 layout-v21，得到**没有语言区的旧布局**，所以：
1. 第一次（FIX3 前）：SettingsActivity `findViewById(R.id.md3_ui_lang_system)` 在旧布局里找不到 → **NPE 闪退**（堆栈 L117）
2. FIX3 加了 NPE 保护后：`optFind` 返回 null，`setCheckedSafe` 跳过 → 设置页**不闪退**，但语言区也**完全不显示**（因为布局里压根就没有这些 View）
3. FIX4 升级了 versionCode，但 layout-v21 还是旧版 → 即使卸载重装，依然看不到语言区

### 为什么之前没排查到？
- 我用 `aapt dump xmltree APK res/layout/activity_settings.xml` 验证，默认 layout 确实有语言区 ✓
- **但从没查过 `res/layout-v21/activity_settings.xml`**，那个是 API21+ 手机真正加载的文件！

## 修复
### 1) 覆盖 `res/layout-v21/activity_settings.xml`
```bash
cp res/layout/activity_settings.xml res/layout-v21/activity_settings.xml
# diff = 0 bytes （两个文件字节级一致）
```
保证：
- 默认 layout：7 RadioButton（语言区 4 + 深色模式 3）✓
- layout-v21：7 RadioButton（语言区 4 + 深色模式 3）✓
- 所有 id 都是 `@+id/`（包括 md3_app_bar / md3_lang_section / 4 个 UI 语言 RadioButton / md3_game_lang_spinner 等）✓

### 2) 再次升级版本号（避免任何缓存残留）
```xml
versionCode="1170031"
versionName="1.17.31"
```

## 验证
```
aapt dump badging → versionCode=1170031, versionName=1.17.31  ✓
aapt dump xmltree res/layout/activity_settings.xml     → RadioButton count = 7  ✓
aapt dump xmltree res/layout-v21/activity_settings.xml → RadioButton count = 7  ✓
```

## 安装提示
请**卸载当前 1.17.30**（或清除应用数据）后，再安装 1.17.31。
打开设置后，顶部在「视觉效果」之上，应该出现第一个 Section：
- 「语言设置」标题
- 「启动器界面语言」卡片（跟随系统/简中/繁中/English，4 个单选）
- 「游戏内语言」卡片（Spinner，Auto + 28 种语言）
