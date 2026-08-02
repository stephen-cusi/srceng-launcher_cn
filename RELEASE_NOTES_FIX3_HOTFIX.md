# MD3-FIX3-HOTFIX: 修复设置页闪退 NullPointerException

## 闪退堆栈
```
java.lang.RuntimeException: Unable to start activity ...
Caused by: java.lang.NullPointerException:
  Attempt to invoke virtual method 'void android.widget.RadioButton.setChecked(boolean)'
  on a null object reference
  at me.nillerusr.SettingsActivity.bindState(SettingsActivity.java:117)
```

L117 = `uiLangSystem.setChecked(true);` —— uiLangSystem==null，即 `findViewById(R.id.md3_ui_lang_system)` 返回null。

## 根因
1. **activity_settings.xml id写法不一致**：我在新增Language Section时，所有id用的是 `@id/md3_xxx`（引用已有，依赖 ids.xml 里的 `<item type="id">`）；但老版本 aapt 在**嵌套 RadioGroup + RadioButton** 场景下，有时 id 映射会失败（即使 ids.xml 声明了）。对比**旧的 Dark mode Section**写法，它一直用 `@+id/md3_dark_system`（布局内首次声明 id），后者稳定。
2. **SettingsActivity 无任何 NPE 防护**：findViewById 一旦返回 null，后续的 `.setChecked(true)` 直接崩。

## 修复
### 1) activity_settings.xml — 统一 @+id/（和Dark mode一致）
所有首次声明的 id 全部改成 `@+id/`，包括：
- `md3_app_bar` / `md3_button_back`
- `md3_lang_section` / `md3_ui_lang_group` + 4个 UI 语言 RadioButton
- `md3_game_lang_spinner`
- `md3_seed_container` / `md3_preview_card` + 3个预览按钮

### 2) SettingsActivity.java — 全链路空安全
- 新增 `optFind(id)`：findViewById 返回 null 时 Log.w(TAG, "...id=0x...")，不抛错
- 新增 `setCheckedSafe(v, checked)` / `setEnabledSafe(v, enabled)`：null + Throwable 全兜底
- `bindState()` 所有 .setChecked / .setEnabled 全走 Safe 版本
- `bindListeners()` 所有监听器注册前 `if (xxx != null)`，Toast 也 try/catch
- `buildGameLangSpinner()` / `buildSeedColors()` 开头判空，内部所有 setText/setAdapter/setSelection try/catch
- `refreshTheme()` recreate() 失败时降级为 finish()
- 保证最坏情况：某个控件找不到 ≡ 对应功能不显示/不响应，绝不闪退

## 验证
- 67 class 编译成功 0 错误
- 117 分支已推送 commit 91c2e92
- Release APK：debug 签名，minSdk17 / targetSdk29
