# Source Engine 启动器 1.12 → 1.17 更新说明

## 概述

本更新将 Source Engine Android 启动器从版本 1.12 升级到版本 1.17 (v1.17.0025)，包含核心 Java 代码、资源文件和配置的全部改动。

---

## 一、Java 核心改动

### 1. `ExtractAssets.java` ([文件链接](file:///workspace/src/me/nillerusr/ExtractAssets.java))

**改动摘要：**
- **VPK 版本号**：`PAK_VERSION` 从 `9` 提升至 `24`（0x18）
- **新方法 `extractAsset()`**：通用资源抽取方法，支持原子写入（先写 `/tmp` 再 rename）和存在性/强制覆盖检查
- **新方法 `extractAssets()`**：主入口。先 chmod 数据目录 `0777`，然后依次抽取：
  - `extras_dir.vpk`（按 PAK_VERSION 判断是否强制）
  - 7 个字体文件（见下文资产部分）
- **`extractVPK(Context)` 新签名**：无 force 参数，内部根据 PAK_VERSION 自动判断
- **旧签名兼容**：保留 `extractVPK(Context, Boolean)` 标记为 @Deprecated，内部重定向到 `extractAssets()`

---

### 2. `ValveActivity2.java` ([文件链接](file:///workspace/src/com/valvesoftware/ValveActivity2.java))

**改动摘要：**

- **`findGameinfo(path)` 返回值变化**：`boolean` → `int`
  | 返回值 | 含义 |
  |--------|------|
  | 0      | 目录无效或缺少 gameinfo.txt |
  | -1     | 有 gameinfo 但 **缺少 platform 目录**（新增错误类型） |
  | 1      | 校验通过 |

- **新增 platform 目录检查**：遍历游戏根目录时额外检查名为 `platform` 的子目录
- **`preInit()` 返回值变化**：`boolean` → `int`（三态，同上）
- **`initNatives()` 默认启动参数**：`-console` → `-nobackgroundlevel`
- **资产抽取调用**：`ExtractAssets.extractVPK(ctx, false)` → `ExtractAssets.extractAssets(ctx)`（适配新 API）

---

### 3. `SDLActivity.java` ([文件链接](file:///workspace/src/org/libsdl/app/SDLActivity.java))

**改动摘要：**

- **新增 Android N+ 持续性能模式**（API 24+）：在加载库成功后、preInit 之前调用：
  ```java
  if (Build.VERSION.SDK_INT >= 24) {
      getWindow().setSustainedPerformanceMode(true);
  }
  ```
  作用：在支持的设备上固定 GPU/CPU 频率，避免游戏过程中因温控导致帧率波动。

- **preInit 返回值处理**：适配新的 `int` 三态返回
  - `preinitResult == -1` 时弹出 `srceng_launcher_error_find_platform`（platform 缺失）
  - `preinitResult == 0` 时弹出原 `srceng_launcher_error_find_gameinfo`（gameinfo 缺失）

---

### 4. `LauncherActivity.java` ([文件链接](file:///workspace/src/me/nillerusr/LauncherActivity.java))

**改动摘要：**
- 命令行参数默认值：`-console` → `-nobackgroundlevel`（与 ValveActivity2 保持一致）

---

## 二、资源与清单改动

### 1. `AndroidManifest.xml` ([链接](file:///workspace/AndroidManifest.xml))

| 项目 | 1.12 | 1.17 |
|------|------|------|
| `android:versionName` | `1.12` | `1.17` |
| SDLActivity `configChanges` | layoutDirection 开头，keyboard/keyboardHidden 在最后 | **keyboard/keyboardHidden 移到最前**（键盘配置变更时不重建 Activity） |
| `<uses-sdk>` 标签 | 有（minSdk=17 / targetSdk=24） | **移除**（改为由构建工具决定） |

---

### 2. 新增/更新字符串（所有语言）

新键：`srceng_launcher_error_find_platform`

| 语言 | 值 |
|------|-----|
| 英文 (en) | `platform directory is missing!` |
| 简体中文 (zh-rCN) | `platform 目录缺失！` |
| 繁体中文 (zh-rTW) | `platform 目錄遺失！` |
| 俄文 (ru) | `Скопируй platform папку бля!` |

文件位置：
- [res/values/strings.xml](file:///workspace/res/values/strings.xml)
- [res/values-zh-rCN/strings.xml](file:///workspace/res/values-zh-rCN/strings.xml)
- [res/values-zh-rTW/strings.xml](file:///workspace/res/values-zh-rTW/strings.xml)
- [res/values-ru/string.xml](file:///workspace/res/values-ru/string.xml)

---

### 3. `colors.xml` ([链接](file:///workspace/res/values/colors.xml))

颜色值加显式 FF 不透明前缀（语义等价，更规范）：
- `hl_color`: `#F79A10` → `#FFF79A10`
- `spin_color`: `#3F3F3F` → `#FF3F3F3F`

---

### 4. Layout 改动

- **`res/layout/activity_launcher.xml`** ([链接](file:///workspace/res/layout/activity_launcher.xml))：
  - `edit_cmdline` 默认文本：`-console` → `-nobackgroundlevel`

- **新增 `res/layout-v21/activity_launcher.xml`** ([链接](file:///workspace/res/layout-v21/activity_launcher.xml))：
  - API 21+ 专用布局，与默认 layout 相同，确保 `android:backgroundTint` 在 Lollipop 以上正确生效

---

## 三、资产文件（assets/）

### 新增字体（7 个）

| 文件名 | 用途 |
|--------|------|
| `DroidSansFallback.ttf` | CJK 回退字体（中文/日文/韩文显示） |
| `dejavusans.ttf` | DejaVu Sans 常规 |
| `dejavusans-bold.ttf` | DejaVu Sans 粗体 |
| `dejavusans-oblique.ttf` | DejaVu Sans 斜体 |
| `dejavusans-boldoblique.ttf` | DejaVu Sans 粗斜体 |
| `LiberationMono-Regular.ttf` | Liberation Mono 等宽字体（控制台/HUD 文本） |
| `Itim-Regular.otf` | Itim 手写风格字体 |

字体抽取逻辑由新的 `ExtractAssets.extractAssets()` 统一处理，首次启动或版本更新时自动释放到 `filesDir/` 并 chmod 0777。

### 更新资产包

- **`extras_dir.vpk`**：从 1.12 版本升级为 1.17 对应版本（VPK_VERSION=24）
  - 体积从 ~数MB 增至 16.7 MB，包含引擎 UI 资源、贴图、粒子等更新

---

## 四、迁移说明与注意事项

1. **VPK 重新解压**：由于 `PAK_VERSION` 从 9 → 24，老用户首次启动 1.17 时会自动强制重新解压 `extras_dir.vpk` 和全部字体。
2. **platform 目录检查**：如果用户资源目录缺少 `platform/`，会收到新的错误提示（而非以前笼统的"找不到资源"），用户定位问题更直接。
3. **默认参数变化**：新的默认 `-nobackgroundlevel` 跳过背景地图加载，启动更快；老用户已保存的设置不受影响（SharedPreferences 中 `argv` 非空时优先取其值）。
4. **性能模式**：Android 7.0 (N) 及以上设备自动启用持续性能模式，帧率更稳；不影响旧设备。
5. **configChanges 顺序**：SDLActivity 键盘相关配置变更前移，确保外接键盘插拔/布局切换时 Activity 不重建，游戏不中断。
