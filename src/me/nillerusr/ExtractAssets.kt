package me.nillerusr

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

open class ExtractAssets {
    companion object {
        @JvmField var TAG = "ExtractAssets"
        private var mPref: SharedPreferences? = null

        const val VPK_NAME = "extras_dir.vpk"
        @JvmField var PAK_VERSION = 24

        private fun chmod(path: String, mode: Int): Int {
            var ret = -1
            try {
                ret = Runtime.getRuntime().exec("chmod ${Integer.toOctalString(mode)} $path").waitFor()
                Log.d(TAG, "chmod ${Integer.toOctalString(mode)} $path: $ret")
            } catch (e: Exception) {
                ret = -1
                Log.d(TAG, "chmod: Runtime not worked: $e")
            }

            try {
                val fileUtils = Class.forName("android.os.FileUtils")
                val setPermissions = fileUtils.getMethod(
                    "setPermissions",
                    String::class.java,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE
                )
                ret = setPermissions.invoke(null, path, mode, -1, -1) as Int
            } catch (e: Exception) {
                ret = -1
                Log.d(TAG, "chmod: FileUtils not worked: $e")
            }
            return ret
        }

        @JvmStatic
        fun extractAsset(context: Context, asset: String, force: Boolean?): Boolean {
            val assetFile = File(context.filesDir, asset)
            val tmp = File(context.filesDir, "$asset.tmp")
            val backup = File(context.filesDir, "$asset.bak")
            var input: InputStream? = null
            var output: FileOutputStream? = null
            try {
                val assetPath = assetFile.path
                val assetExists = assetFile.exists()
                if (!force!! && assetExists) return true

                var written = 0L
                input = context.assets.open(asset)
                output = FileOutputStream(tmp)
                val buffer = ByteArray(8192)
                while (true) {
                    val length = input!!.read(buffer)
                    if (length <= 0) break
                    output!!.write(buffer, 0, length)
                    written += length
                }
                output!!.fd.sync()
                output!!.close()
                output = null
                input!!.close()
                input = null
                if (written <= 0 || tmp.length() != written) throw java.io.IOException("Incomplete asset copy")
                if (backup.exists() && !backup.delete()) throw java.io.IOException("Failed to remove stale asset backup")
                if (assetExists && !assetFile.renameTo(backup)) throw java.io.IOException("Failed to preserve existing asset")
                if (!tmp.renameTo(assetFile)) {
                    if (backup.exists()) backup.renameTo(assetFile)
                    throw java.io.IOException("Failed to install extracted asset")
                }
                if (backup.exists()) backup.delete()
                chmod(assetPath, 0x1ff)
                return true
            } catch (e: Exception) {
                if (!assetFile.exists() && backup.exists() && !backup.renameTo(assetFile)) {
                    Log.e("SRCAPK", "Failed to restore previous $asset")
                }
                Log.e("SRCAPK", "Failed to extract $asset:$e")
                return false
            } finally {
                try { output?.close() } catch (_: Exception) {}
                try { input?.close() } catch (_: Exception) {}
                if (tmp.exists()) tmp.delete()
            }
        }

        @JvmStatic
        fun extractAssets(context: Context) {
            chmod(context.applicationInfo.dataDir, 0x1ff)
            chmod(context.filesDir.path, 0x1ff)
            extractVPK(context)
            extractAsset(context, "DroidSansFallback.ttf", false)
            extractAsset(context, "LiberationMono-Regular.ttf", false)
            extractAsset(context, "dejavusans-boldoblique.ttf", false)
            extractAsset(context, "dejavusans-bold.ttf", false)
            extractAsset(context, "dejavusans-oblique.ttf", false)
            extractAsset(context, "dejavusans.ttf", false)
            extractAsset(context, "Itim-Regular.otf", false)
        }

        @JvmStatic
        fun extractVPK(context: Context) {
            if (mPref == null) mPref = context.getSharedPreferences("mod", 0)
            val version = mPref!!.getInt("pakversion", 0)
            if (extractAsset(context, VPK_NAME, version != PAK_VERSION)) {
                mPref!!.edit().putInt("pakversion", PAK_VERSION).apply()
            }
        }

        @Deprecated("Old API kept for compatibility")
        @JvmStatic
        fun extractVPK(context: Context, force: Boolean?) {
            extractAssets(context)
        }
    }
}
