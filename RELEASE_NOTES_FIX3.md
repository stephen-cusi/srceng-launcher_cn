# MD3-FIX3: 新增启动器语言选择 + 游戏-language启动参数

## 新增功能
### 1. 启动器 UI 语言（4个选项）
设置 -> 语言设置 -> 界面语言：
- 跟随系统（默认）
- 简体中文
- 繁體中文
- English

切换后立即 recreate() 生效，文字资源即时切换。

### 2. 游戏语言（28种 + Auto）
设置 -> 语言设置 -> 游戏语言：
下拉列表格式为「友好显示名 · Source引擎码」，例如：
- Auto (don't force) -> 不追加参数，游戏自行根据资源/环境
- 简体中文 · schinese
- 繁體中文 · tchinese
- English · english
- 其他：russian / german / french / italian / spanish / brazilian / latam / japanese / korean / polish / dutch / czech / danish / finnish / greek / hungarian / norwegian / portuguese / romanian / swedish / thai / turkish / ukrainian / bulgarian

### 3. 语言参数透明追加（用户看不见）
ValveActivity2.initNatives() 组装 argv（-game hl2 <用户cmdline>）后，若设置了游戏语言，自动在末尾追加：
  -language schinese
- 语言代码先过正则 [a-z_]+ 防argv注入
- 不修改用户在启动器里填的cmdLine文本（不会出现在EditText里，避免和用户手动加的 -language 混在一起）；如果用户自己也加了，优先级以最右为准

## 修复了一个会导致编译失败的XML坑
zh-CN/zh-TW 的 md3_game_lang_desc 里写了 <代码> / <代碼>，<> 被aapt当XML标签报 mismatched tag，导致 aapt 未生成 R.java，后续所有 R.string/R.id 引用全报 package does not exist。改为 {代码} / {代碼}。

## APK
source-engine-1.17-MD3-FIX3-debug.apk（debug签名，minSdk17/targetSdk29，package com.valvesoftware.source）
