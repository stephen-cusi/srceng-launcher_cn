# MD3-FIX9-HOTFIX7: 修复「切换深色模式/主题色也被全栈重启」问题

## 用户反馈
「为什么我除了切换主界面语言 切换主题 主题色也会重启界面呢」

## 根因
HOTFIX5为了解决语言切换主界面不刷新的问题，把`refreshTheme()`改成了——无论什么变化（Language/Dark mode/Dynamic color/SeedColor）——**统一走`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`清空任务栈+重启LauncherActivity**。

副作用就是：
- 切深色模式：SettingsActivity→Launcher全栈重启，用户感知「整个App被重启了」
- 切主题色（SeedColor）：也被全栈重启了！！主题色变化其实根本不需要重新加载strings资源，只要重建Md3Tokens并重绘View即可

## 修复：三档刷新策略（SettingsActivity.refreshTheme level参数化）

### 档1 `REFRESH_TOKEN_REDRAW`（仅SeedColor变化）—— **零重启零重建**
```
触发条件：用户手动切换主题色(4个圆形颜色预览)
动作：
  - applyAfterSetContentView(this) 重新生成Md3Tokens+重绘所有卡片/按钮/文字颜色
  - buildSeedColors()刷新颜色圆圈选中环颜色
  - 不调用任何recreate()/CLEAR_TASK
用户感知：设置页颜色立即刷新，回主界面Launcher onResume也会applyAfterSetContentView重绘，完全无重启
```

### 档2 `REFRESH_RECREATE`（深色/动态取色）—— **只重建SettingsActivity**
```
触发条件：深色模式(跟随/关/开)、动态取色开关
动作：
  - recreate() SettingsActivity自己
  - LauncherActivity不被重建（onResume时比较cachedThemeMode/cachedDynamic/cachedSeedColor）
    → Launcher.onResume中调用applyBeforeOnCreate + applyAfterSetContentView，
      重新注入ThemeMode+重新生成Md3Tokens+重绘View
用户感知：设置页重新加载（很快），回主界面立刻看到新主题生效，任务栈不重启
```

### 档3 `REFRESH_FULL_RESTART`（仅语言变化）—— **全栈重启Launcher**
```
触发条件：UI语言变化
动作：NEW_TASK + CLEAR_TASK + finishAffinity()
原因：LayoutInflater缓存的strings资源/Context包装必须全栈重建才会刷新
语言变化SettingsActivity单独recreate完全不够，所以保留这一档
```

## 配套改动：LauncherActivity加onResume主题缓存自动刷新
```java
// 进入Launcher.onCreate时记录cachedThemeMode/cachedDynamic/cachedSeedColor
// 每次onResume从SharedPreferences重新读，如果变化了立刻重绘（不recreate）
if (newMode != cachedThemeMode || newDyn != cachedDynamic || newSeed != cachedSeedColor) {
    applyBeforeOnCreate(this);   // 重新注入Theme + Locale
    applyAfterSetContentView(this);  // 重新生成Md3Tokens,所有View立刻变颜色
}
```

## versionCode
1170034 → **1170035**（1.17.34→1.17.35）
