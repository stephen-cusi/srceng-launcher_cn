package com.valvesoftware

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.util.Locale
import me.nillerusr.ExtractAssets
import me.nillerusr.LauncherActivity
import me.nillerusr.md3.Md3Theme

class ValveActivity2 {
    companion object {
        @JvmField
        var mPref: SharedPreferences? = null

        const val PREF_SKIP_INTRO = "skip_intro_video"

        @JvmStatic
        external fun setArgs(args: String)

        @JvmStatic
        external fun setenv(name: String, value: String, overwrite: Int): Int

        @JvmStatic
        private external fun nativeOnActivityResult(
            activity: Activity,
            requestCode: Int,
            resultCode: Int,
            intent: Intent
        )

        @JvmStatic
        fun findGameinfo(path: String): Int {
            val directory = File(path)
            if (!directory.isDirectory) return 0

            var hasPlatform = false
            var hasGameinfo = false
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    hasGameinfo = hasGameinfo || file.listFiles()?.any {
                        it.name.lowercase(Locale.ROOT) == "gameinfo.txt"
                    } == true
                }
                if (file.name.lowercase(Locale.ROOT) == "platform") hasPlatform = true
            }

            if (!hasGameinfo) return 0
            return if (hasPlatform) 1 else -1
        }

        @JvmStatic
        fun isModGameinfoExists(path: String): Boolean {
            val directory = File(path)
            if (!directory.isDirectory) return false
            return directory.listFiles()?.any {
                it.isFile && it.name.lowercase(Locale.ROOT) == "gameinfo.txt"
            } == true
        }

        @JvmStatic
        fun preInit(context: Context, intent: Intent): Int {
            val preferences = context.getSharedPreferences("mod", 0)
            mPref = preferences
            val gamePath = preferences.getString(
                "gamepath",
                LauncherActivity.getDefaultDir() + "/srceng"
            ) ?: LauncherActivity.getDefaultDir() + "/srceng"
            val gameDirectory = intent.getStringExtra("gamedir").orEmpty().ifEmpty { "hl2" }

            val gameinfo = findGameinfo(gamePath)
            if (gameinfo == 0 || !isModGameinfoExists("$gamePath/$gameDirectory")) return 0
            return if (gameinfo == -1) -1 else 1
        }

        @JvmStatic
        fun initNatives(context: Context, intent: Intent) {
            val preferences = context.getSharedPreferences("mod", 0)
            mPref = preferences
            val applicationInfo = context.applicationInfo
            val gamePath = preferences.getString(
                "gamepath",
                LauncherActivity.getDefaultDir() + "/srceng"
            ) ?: LauncherActivity.getDefaultDir() + "/srceng"
            val gameDirectory = intent.getStringExtra("gamedir").orEmpty().ifEmpty { "hl2" }
            val gameLibraryDirectory = intent.getStringExtra("gamelibdir")
            val customVpk = intent.getStringExtra("vpk")
            var arguments = intent.getStringExtra("argv").orEmpty()
            Log.v("SRCAPK", "argv=$arguments")

            if (arguments.isEmpty()) {
                arguments = preferences.getString("argv", "-nobackgroundlevel")
                    ?: "-nobackgroundlevel"
            }
            arguments = "-game $gameDirectory $arguments"

            val gameLanguage = Md3Theme.getGameLang(context)
            if (gameLanguage.matches(Regex("[a-z_]+"))) {
                arguments = arguments
                    .replace(Regex("(?i)(^|\\s)-language(?:\\s+|=)\\S+"), "$1")
                    .trim()
                arguments += " -language $gameLanguage"
            }

            if (preferences.getBoolean(PREF_SKIP_INTRO, false)) {
                arguments = arguments.replace(Regex("(?i)(^|\\s)-novid(?:\\s|$)"), "$1").trim()
                arguments += " -novid"
            }

            arguments = arguments
                .replace(Regex("(?i)(^|\\s)-(?:w|width|h|height)(?:\\s+|=)\\S+"), "$1")
                .trim()
            val resolution = Md3Theme.getResolvedResolution(context)
            if (resolution.size >= 2) {
                val width = resolution[0]
                val height = resolution[1]
                if (width in 320..8192 && height in 240..8192) {
                    arguments += " -w $width -h $height"
                }
            }

            if (!gameLibraryDirectory.isNullOrEmpty()) setenv("APP_MOD_LIB", gameLibraryDirectory, 1)

            ExtractAssets.extractAssets(context)
            var vpks = context.filesDir.path + "/" + ExtractAssets.VPK_NAME
            if (!customVpk.isNullOrEmpty()) vpks = "$customVpk,$vpks"
            Log.v("SRCAPK", "vpks=$vpks")

            setenv("EXTRAS_VPK_PATH", vpks, 1)
            setenv("LANG", Md3Theme.getRealSystemLocale().toString(), 1)
            setenv("APP_DATA_PATH", applicationInfo.dataDir, 1)
            setenv("APP_LIB_PATH", applicationInfo.nativeLibraryDir, 1)
            setenv(
                "VALVE_GAME_PATH",
                if (preferences.getBoolean("rodir", false)) LauncherActivity.getAndroidDataDir() else gamePath,
                1
            )

            Log.v("SRCAPK", "argv=$arguments")
            setArgs(arguments)
        }
    }
}
