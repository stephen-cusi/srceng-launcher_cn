package me.nillerusr

import android.app.Activity
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

class PredictiveBackController(
    private val activity: Activity,
    private val onBack: () -> Unit
) {
    private var callback: OnBackInvokedCallback? = null

    fun sync() {
        if (Build.VERSION.SDK_INT < 33) return
        val enabled = activity.getSharedPreferences(PREFS_NAME, 0).getBoolean(PREF_KEY, true)
        if (enabled && callback == null) {
            callback = OnBackInvokedCallback(onBack).also {
                activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    it
                )
            }
        } else if (!enabled) {
            release()
        }
    }

    fun release() {
        if (Build.VERSION.SDK_INT < 33) return
        callback?.let { activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        callback = null
    }

    companion object {
        const val PREFS_NAME = "mod"
        const val PREF_KEY = "predictive_back"
    }
}
