# MD3-FIX7-HOTFIX5: 语言切换主界面不刷新+Locale链路彻底加固

## 用户反馈
1. **设置页语言变了，主界面不变**：从设置切语言，设置自己的界面文字变了，但**返回主界面（LauncherActivity）文字还是旧语言** —— 必须重启App才能彻底生效
2. **主界面多语言不全**：设置有、主界面没有

## 根因
### 根因 1：`refreshTheme()` 只重建 SettingsActivity，**主界面 LauncherActivity 还在任务栈里（旧Locale缓存）完全不重建**
```java
// 旧refreshTheme：只重建自己
recreate(); ← 只重建SettingsActivity
finishAffinity? 没有 ← LauncherActivity还在旧栈！
```
Android任务栈逻辑：SettingsActivity.onBackStack，下面的LauncherActivity.onCreate早已在之前的Locale下运行，inflater缓存的strings/Theme/Resources全是旧的。仅recreate当前Activity永远无法让**栈底的Launcher**生效。

### 根因 2：只有 onCreate 的 applyBeforeOnCreate 注入Locale，**attachBaseContext**没有注入
Android系统在`attachBaseContext`中创建第一个Context，LayoutInflater/Resources等底层都是基于这个Context——如果只在onCreate才改Locale，部分底层Inflation可能已经按旧Locale缓存。

## 修复
### 修复 1：`refreshTheme()` → **任务栈级重启**（CLEAR_TASK + NEW_TASK）
```java
Intent i = new Intent(this, LauncherActivity.class);
i.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
startActivity(i);
finishAffinity();
```
→ 彻底清空任务栈 → **LauncherActivity + 将来的SettingsActivity都是全新onCreate + 全新attachBaseContext**
→ 100% 会刷新Locale/Theme（连Launcher都重新走一遍onCreate）

### 修复 2：**LauncherActivity + SettingsActivity 双加attachBaseContext注入Locale**
在Context创建最早时机（attachBaseContext）就用`createConfigurationContext`包一层正确Locale的Context，保证后续所有LayoutInflater/Resources都是目标语言：
```java
@Override
protected void attachBaseContext(Context newBase) {
    Configuration cfg = new Configuration(newBase.getResources().getConfiguration());
    Md3Theme.applyUiLocaleConfiguration(cfg, Md3Theme.getUiLang(newBase));
    super.attachBaseContext(newBase.createConfigurationContext(cfg));
}
```
→ onCreate时再调applyBeforeOnCreate做双保险

### 修复 3：Md3Theme 对外暴露Locale链路API（供attachBaseContext使用）
- `getRealSystemLocale()` 私有 → public
- 新增 `resolveUiLangLocale(uiLang)` → 计算目标Locale
- 新增 `applyUiLocaleConfiguration(cfg, uiLang)` → 只改传入Configuration的Locale，不碰Activity（专用于attachBaseContext内部）

### 修复 4：英文默认strings资源完善
把主界面关键英文键重新校对（`srceng_launcher_env`→`Environment variables`，`srceng_update`→`Update available!`等）

## 验证
- class=68, 0 errors
- versionCode 1170033 / versionName 1.17.33
- LauncherActivity 和 SettingsActivity 都有attachBaseContext方法
- refreshTheme为：NEW_TASK + CLEAR_TASK + finishAffinity
