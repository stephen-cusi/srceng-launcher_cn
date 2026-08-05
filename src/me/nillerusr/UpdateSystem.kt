package me.nillerusr

import android.content.Context
import android.os.AsyncTask
import com.valvesoftware.source.R
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
open class UpdateSystem : AsyncTask<Void, Void, UpdateSystem.Result> {
    interface Callback {
        fun onUpdateResult(result: Result)
    }

    interface MirrorTestCallback {
        fun onMirrorTestResult(available: BooleanArray)
    }

    fun interface ChangelogCallback {
        fun onResult(version: String, changelog: String?, error: String?)
    }

    open class Result {
        @JvmField var success = false
        @JvmField var published = false
        @JvmField var available = false
        @JvmField var error: String? = null
        @JvmField var versionName: String? = null
        @JvmField var build = 0
        @JvmField var apkUrl: String? = null
        @JvmField var changelogUrl: String? = null
        @JvmField var changelog: String? = null
        @JvmField var sha256: String? = null
    }

    private val context: Context
    private val channel: String
    private val mirror: String
    private val callback: Callback?

    constructor(context: Context) : this(context, CHANNEL_STABLE, MIRROR_AUTO, null)

    constructor(context: Context, channel: String?, mirror: String?, callback: Callback?) {
        this.context = context.applicationContext
        this.channel = if (CHANNEL_DEV == channel) CHANNEL_DEV else CHANNEL_STABLE
        this.mirror = if (isKnownMirror(mirror)) mirror!! else MIRROR_AUTO
        this.callback = callback
    }

    override fun doInBackground(vararg ignored: Void?): Result {
        val result = Result()
        var lastError: Exception? = null
        val candidates = if (MIRROR_AUTO == mirror) {
            probeFastestMirror(channel)?.let { arrayOf(it) } ?: AUTO_MIRRORS
        } else {
            arrayOf(mirror)
        }
        for (candidate in candidates) {
            try {
                val manifestUrl = cacheBust(mirrorUrl(RAW_BASE + channel + "/manifest.json", candidate)!!)
                val manifest = JSONObject(fetchText(manifestUrl, 10000, 15000))
                if (!manifest.optBoolean("published", true)) {
                    result.success = true
                    result.published = false
                    result.available = false
                    return result
                }
                result.published = true
                result.versionName = manifest.getString("versionName")
                result.build = manifest.getInt("build")
                result.apkUrl = mirrorUrl(manifest.getString("apkUrl"), candidate)
                result.changelogUrl = mirrorUrl(manifest.getString("changelogUrl"), candidate)
                result.sha256 = manifest.optString("sha256", "")
                result.changelog = fetchText(result.changelogUrl!!, 10000, 15000)

                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                val localBuild = context.resources.getInteger(R.integer.update_build)
                result.available = result.versionName != info.versionName && result.build > localBuild
                result.success = true
                return result
            } catch (error: Exception) {
                lastError = error
            }
        }
        result.error = lastError?.message ?: lastError.toString()
        return result
    }

    override fun onPostExecute(result: Result) {
        callback?.onUpdateResult(result)
    }

    companion object {
        const val CHANNEL_STABLE = "stable"
        const val CHANNEL_DEV = "dev"
        const val PREF_CHANNEL = "update_channel"
        const val PREF_MIRROR = "update_mirror"
        const val MIRROR_AUTO = "auto"

        @JvmField
        val MIRROR_IDS = arrayOf("github", "jsdelivr", "jsdelivr_fastly", "jsdelivr_cloudflare", "ghproxy_net", "gh_proxy_com")

        @JvmField
        val MIRROR_NAMES = arrayOf("GitHub Raw", "jsDelivr", "jsDelivr Fastly", "jsDelivr Cloudflare", "ghproxy.net", "gh-proxy.com")

        private const val RAW_BASE = "https://raw.githubusercontent.com/stephen-cusi/srceng-launcher-updates/main/"
        private val AUTO_MIRRORS = arrayOf("github", "jsdelivr", "jsdelivr_fastly", "jsdelivr_cloudflare", "ghproxy_net", "gh_proxy_com")

        @JvmStatic
        fun testMirrors(callback: MirrorTestCallback?) {
            object : AsyncTask<Void, Void, BooleanArray>() {
                override fun doInBackground(vararg ignored: Void?): BooleanArray {
                    val results = BooleanArray(MIRROR_IDS.size)
                    val executor = Executors.newFixedThreadPool(MIRROR_IDS.size)
                    val futures = MIRROR_IDS.map { source ->
                        executor.submit(Callable {
                            try {
                                val manifestUrl = cacheBust(mirrorUrl(RAW_BASE + CHANNEL_DEV + "/manifest.json", source)!!)
                                val manifest = JSONObject(fetchText(manifestUrl, 6000, 8000))
                                manifest.optBoolean("published", false) && probeDownload(mirrorUrl(manifest.getString("apkUrl"), source)!!)
                            } catch (_: Exception) {
                                false
                            }
                        })
                    }
                    for (i in futures.indices) {
                        try {
                            results[i] = futures[i].get()
                        } catch (_: Exception) {
                        }
                    }
                    executor.shutdownNow()
                    return results
                }

                override fun onPostExecute(results: BooleanArray) {
                    callback?.onMirrorTestResult(results)
                }
            }.execute()
        }

        @JvmStatic
        fun loadCurrentChangelog(context: Context, callback: ChangelogCallback) {
            val appContext = context.applicationContext
            object : AsyncTask<Void, Void, Array<String?>>() {
                override fun doInBackground(vararg ignored: Void?): Array<String?> {
                    val version = try {
                        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: ""
                    } catch (error: Exception) {
                        return arrayOf("", null, error.message ?: error.toString())
                    }
                    val channel = if ("-dev" in version) CHANNEL_DEV else CHANNEL_STABLE
                    val configuredMirror = appContext.getSharedPreferences("mod", 0)
                        .getString(PREF_MIRROR, MIRROR_AUTO)
                    val candidates = if (MIRROR_AUTO == configuredMirror) {
                        probeFastestMirror(channel)?.let { arrayOf(it) } ?: AUTO_MIRRORS
                    } else {
                        arrayOf(configuredMirror)
                    }
                    var lastError: Exception? = null
                    for (candidate in candidates) {
                        try {
                            val rawUrl = RAW_BASE + channel + "/changelog/" + version + ".md"
                            val address = cacheBust(mirrorUrl(rawUrl, candidate)!!)
                            return arrayOf(version, fetchText(address, 10000, 15000), null)
                        } catch (error: Exception) {
                            lastError = error
                        }
                    }
                    return arrayOf(version, null, lastError?.message ?: lastError.toString())
                }

                override fun onPostExecute(result: Array<String?>) {
                    callback.onResult(result[0] ?: "", result[1], result[2])
                }
            }.execute()
        }

        private fun isKnownMirror(value: String?): Boolean = value == MIRROR_AUTO || MIRROR_IDS.any { it == value }

        @JvmStatic
        private fun probeFastestMirror(channel: String?): String? {
            val executor = Executors.newFixedThreadPool(AUTO_MIRRORS.size)
            val tasks = AUTO_MIRRORS.map { source ->
                Callable<Pair<String, Long>?> {
                    try {
                        val start = System.currentTimeMillis()
                        val manifestUrl = cacheBust(mirrorUrl(RAW_BASE + channel + "/manifest.json", source)!!)
                        val manifest = JSONObject(fetchText(manifestUrl, 6000, 8000))
                        if (!manifest.optBoolean("published", false)) return@Callable null
                        if (!probeDownload(mirrorUrl(manifest.getString("apkUrl"), source)!!)) return@Callable null
                        source to (System.currentTimeMillis() - start)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            val futures = try {
                executor.invokeAll(tasks, 10000, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                emptyList()
            }
            var best: Pair<String, Long>? = null
            for (future in futures) {
                try {
                    val candidate = future.get()
                    if (candidate != null && (best == null || candidate.second < best.second)) {
                        best = candidate
                    }
                } catch (_: Exception) {
                }
            }
            executor.shutdownNow()
            return best?.first
        }

        @JvmStatic
        fun mirrorUrl(rawUrl: String?, mirror: String?): String? {
            if (rawUrl == null || mirror == "github") return rawUrl
            val prefix = "https://raw.githubusercontent.com/"
            if (!rawUrl.startsWith(prefix)) return rawUrl
            val path = rawUrl.substring(prefix.length)
            return when (mirror) {
                "jsdelivr" -> "https://cdn.jsdelivr.net/gh/${toJsDelivrPath(path)}"
                "jsdelivr_fastly" -> "https://fastly.jsdelivr.net/gh/${toJsDelivrPath(path)}"
                "jsdelivr_cloudflare" -> "https://testingcf.jsdelivr.net/gh/${toJsDelivrPath(path)}"
                "ghproxy_net" -> "https://ghproxy.net/$rawUrl"
                "gh_proxy_com" -> "https://gh-proxy.com/$rawUrl"
                else -> rawUrl
            }
        }

        private fun toJsDelivrPath(path: String): String {
            val parts = path.split("/", limit = 4)
            return if (parts.size == 4) "${parts[0]}/${parts[1]}@${parts[2]}/${parts[3]}" else path
        }

        private fun cacheBust(address: String): String =
            address + if ('?' in address) "&update=${System.currentTimeMillis()}" else "?update=${System.currentTimeMillis()}"

        @Throws(Exception::class)
        private fun probeDownload(address: String): Boolean {
            val connection = URL(address).openConnection() as HttpURLConnection
            connection.connectTimeout = 6000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "srceng-launcher-mirror-test")
            connection.setRequestProperty("Range", "bytes=0-1023")
            try {
                val status = connection.responseCode
                if (status != 200 && status != 206) return false
                connection.inputStream.use { input -> return input.read() == 80 && input.read() == 75 }
            } finally {
                connection.disconnect()
            }
        }

        @Throws(Exception::class)
        private fun fetchText(address: String, connectTimeout: Int, readTimeout: Int): String {
            val connection = URL(address).openConnection() as HttpURLConnection
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            connection.setRequestProperty("User-Agent", "srceng-launcher-update-checker")
            connection.setRequestProperty("Cache-Control", "no-cache")
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw Exception("HTTP $status")
                connection.inputStream.use { input ->
                    val text = StringBuilder()
                    BufferedReader(InputStreamReader(input, "UTF-8")).useLines { lines ->
                        lines.forEach { text.append(it).append('\n') }
                    }
                    return text.toString().trim()
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
