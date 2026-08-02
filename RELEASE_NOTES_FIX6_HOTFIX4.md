# MD3-FIX6-HOTFIX4: 修复「跟随系统不生效 + UI语言扩展至10种

## 用户反馈
安装 1.17.31 后基本正常，但两个问题：

1. **跟随系统不生效**：切换到英文→再切回「跟随系统」→还是英文。比如系统语言是简体中文，切回跟随系统后依然显示英文（或之前手动选的语言）
2. **UI语言太少**：希望UI语言除了简中/繁中/英文，再加上俄语（Русский）、日语（日本語）、韩语（한국어）、法语（Français）、德语（Deutsch）、西语（Español）

## 修复
### 修复1: 跟随系统Locale不刷新（根因是Locale.getDefault()被我们污染）
**根因：
```java
// applyUiLocale():
Locale desired = (target == null) ? Locale.getDefault() : target;
```
但我们每次选非系统语言（比如英文）时，我们会调用：
```java
Locale.setDefault(ENGLISH);  // 把JVM全局默认Locale改成了英文！
```
等用户再切回「跟随系统」时：
- desired = Locale.getDefault() → 现在是 ENGLISH（被我们之前的Locale.setDefault污染）
- **实际系统的真实Locale（比如简体中文）根本没被读到
- desired.equals(cur) → true，Configuration完全不刷新→还是英文！

**修复**：
- 新增 `getRealSystemLocale()`：
  - 从 `Resources.getSystem().getConfiguration()` 读取系统**真实**Locale（这是Android系统的只读Configuration，**永远不会被我们Locale.setDefault污染
  - Resources.getSystem()返回系统资源Configuration，不是我们App自己的resources）
  - API24+：`cfg.getLocales().get(0) ；否则 cfg.locale
- applyUiLocale()所有「跟随系统」分支：
  - desired = getRealSystemLocale() ✅ 不再用Locale.getDefault()
  - cur fallback也改成getRealSystemLocale()，保证即使我们App的cur=getRealSystemLocale()都改Configuration

### 修复2: 启动器UI语言扩展至10种 + UI语言从4个一行RadioButton扩展为Spinner下拉，可轻松放10种（一行放10种：
```
跟随系统 | 简体中文 | 繁體中文 | English |
Русский | 日本語 | 한국어 | Français | Deutsch | Español
```
#### UI重构：
- 删除layout/和layout-v21/两个布局里UI语言区：
  - 删除RadioGroup md3_ui_lang_group和4个RadioButton
  - 改为 md3_ui_lang_spinner Spinner控件+标题+副标题（启动器界面语言，和游戏语言布局一样）
- SettingsActivity：
  - 删除uiLangGroup/uiLangSystem/uiLangZhCn/uiLangZhTw/uiLangEn字段
  - 新增buildUiLangSpinner()（和buildGameLangSpinner()同模式ArrayAdapter + MD3风格的背景+文本样式
  - 新增uiLangDisplayName()：母语名 + · + 值，资源名 + 代码格式，例：Русский · ru
- 新增string资源新增6种语言label（3套strings同步：values、values-zh-rCN、values-zh-rTW），并在zh-rCN里加中文注释：Русский · 俄语等
- Md3Theme：
  - 新增6个UI_LANG_*常量 UI_LANG_RU/UI_LANG_JA/UI_LANG_KO/UI_LANG_FR/UI_LANG_DE/UI_LANG_ES
  - localeForUiLang()扩展10种Locale映射：ru_RU/JAPAN/KOREA/FRANCE/GERMANY/es_ES
  - UI_LANG_VALUES数组 扩到10种

## 验证
- class count 68（之前67，代码增加）0 errors
- layout RadioButton数量=3（深色模式3，语言区已改为2个Spinner了！✓
- layout Spinner=2个（启动器界面语言+游戏语言）
- layout-v21同样同步，两个布局内容字节级相同
- strings资源md3_ui_lang_ru/ja/ko/fr/de/es=40个条目（3个values目录×6种语言+重复资源索引）
- versionCode 1170032 / versionName 1.17.32
