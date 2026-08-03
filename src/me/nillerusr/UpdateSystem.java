package me.nillerusr;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.AsyncTask;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class UpdateSystem extends AsyncTask<Void, Void, UpdateSystem.Result> {
    public static final String CHANNEL_STABLE = "stable";
    public static final String CHANNEL_DEV = "dev";
    public static final String PREF_CHANNEL = "update_channel";
    public static final String PREF_MIRROR = "update_mirror";
    public static final String MIRROR_AUTO = "auto";
    public static final String[] MIRROR_IDS = new String[]{
            "github", "jsdelivr", "jsdelivr_fastly", "jsdelivr_cloudflare", "ghproxy_net", "gh_proxy_com"
    };
    public static final String[] MIRROR_NAMES = new String[]{
            "GitHub Raw", "jsDelivr", "jsDelivr Fastly", "jsDelivr Cloudflare", "ghproxy.net", "gh-proxy.com"
    };
    private static final String RAW_BASE = "https://raw.githubusercontent.com/stephen-cusi/srceng-launcher-updates/main/";

    public interface Callback {
        void onUpdateResult(Result result);
    }

    public interface MirrorTestCallback {
        void onMirrorTestResult(boolean[] available);
    }

    public static class Result {
        public boolean success;
        public boolean published;
        public boolean available;
        public String error;
        public String versionName;
        public int build;
        public String apkUrl;
        public String changelogUrl;
        public String changelog;
        public String sha256;
    }

    private final Context context;
    private final String channel;
    private final String mirror;
    private final Callback callback;

    public UpdateSystem(Context context) {
        this(context, CHANNEL_STABLE, MIRROR_AUTO, null);
    }

    public UpdateSystem(Context context, String channel, String mirror, Callback callback) {
        this.context = context.getApplicationContext();
        this.channel = CHANNEL_DEV.equals(channel) ? CHANNEL_DEV : CHANNEL_STABLE;
        this.mirror = isKnownMirror(mirror) ? mirror : MIRROR_AUTO;
        this.callback = callback;
    }

    @Override
    protected Result doInBackground(Void... ignored) {
        Result result = new Result();
        Exception lastError = null;
        String[] candidates = MIRROR_AUTO.equals(mirror) ? new String[]{
                "jsdelivr", "jsdelivr_fastly", "jsdelivr_cloudflare", "ghproxy_net", "gh_proxy_com", "github"
        } : new String[]{mirror};
        for (String candidate : candidates) {
            try {
                JSONObject manifest = new JSONObject(fetchText(
                        mirrorUrl(RAW_BASE + channel + "/manifest.json", candidate), 10000, 15000));
                if (!manifest.optBoolean("published", true)) {
                    result.success = true;
                    result.published = false;
                    result.available = false;
                    return result;
                }
                result.published = true;
                result.versionName = manifest.getString("versionName");
                result.build = manifest.getInt("build");
                result.apkUrl = mirrorUrl(manifest.getString("apkUrl"), candidate);
                result.changelogUrl = mirrorUrl(manifest.getString("changelogUrl"), candidate);
                result.sha256 = manifest.optString("sha256", "");
                result.changelog = fetchText(result.changelogUrl, 10000, 15000);

                PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                int localBuild = context.getResources().getInteger(com.valvesoftware.source.R.integer.update_build);
                result.available = !result.versionName.equals(info.versionName) && result.build > localBuild;
                result.success = true;
                return result;
            } catch (Exception e) {
                lastError = e;
            }
        }
        result.error = lastError == null || lastError.getMessage() == null
                ? String.valueOf(lastError) : lastError.getMessage();
        return result;
    }

    @Override
    protected void onPostExecute(Result result) {
        if (callback != null) callback.onUpdateResult(result);
    }

    public static void testMirrors(final MirrorTestCallback callback) {
        new AsyncTask<Void, Void, boolean[]>() {
            @Override protected boolean[] doInBackground(Void... ignored) {
                final boolean[] results = new boolean[MIRROR_IDS.length];
                ExecutorService executor = Executors.newFixedThreadPool(MIRROR_IDS.length);
                Future<Boolean>[] futures = new Future[MIRROR_IDS.length];
                for (int i = 0; i < MIRROR_IDS.length; i++) {
                    final String source = MIRROR_IDS[i];
                    futures[i] = executor.submit(new Callable<Boolean>() {
                        @Override public Boolean call() {
                            try {
                                JSONObject manifest = new JSONObject(fetchText(mirrorUrl(
                                        RAW_BASE + CHANNEL_DEV + "/manifest.json", source), 6000, 8000));
                                if (!manifest.optBoolean("published", false)) return false;
                                return probeDownload(mirrorUrl(manifest.getString("apkUrl"), source));
                            } catch (Exception ignored) {
                                return false;
                            }
                        }
                    });
                }
                for (int i = 0; i < futures.length; i++) {
                    try { results[i] = futures[i].get(); } catch (Exception error) {}
                }
                executor.shutdownNow();
                return results;
            }

            @Override protected void onPostExecute(boolean[] results) {
                if (callback != null) callback.onMirrorTestResult(results);
            }
        }.execute();
    }

    private static boolean isKnownMirror(String value) {
        if (MIRROR_AUTO.equals(value)) return true;
        for (String id : MIRROR_IDS) if (id.equals(value)) return true;
        return false;
    }

    public static String mirrorUrl(String rawUrl, String mirror) {
        if (rawUrl == null || "github".equals(mirror)) return rawUrl;
        String prefix = "https://raw.githubusercontent.com/";
        if (!rawUrl.startsWith(prefix)) return rawUrl;
        String path = rawUrl.substring(prefix.length());
        if ("jsdelivr".equals(mirror)) return "https://cdn.jsdelivr.net/gh/" + toJsDelivrPath(path);
        if ("jsdelivr_fastly".equals(mirror)) return "https://fastly.jsdelivr.net/gh/" + toJsDelivrPath(path);
        if ("jsdelivr_cloudflare".equals(mirror)) return "https://testingcf.jsdelivr.net/gh/" + toJsDelivrPath(path);
        if ("ghproxy_net".equals(mirror)) return "https://ghproxy.net/" + rawUrl;
        if ("gh_proxy_com".equals(mirror)) return "https://gh-proxy.com/" + rawUrl;
        return rawUrl;
    }

    private static String toJsDelivrPath(String path) {
        String[] parts = path.split("/", 4);
        return parts.length == 4 ? parts[0] + "/" + parts[1] + "@" + parts[2] + "/" + parts[3] : path;
    }

    private static boolean probeDownload(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "srceng-launcher-mirror-test");
        connection.setRequestProperty("Range", "bytes=0-1023");
        try {
            int status = connection.getResponseCode();
            if (status != 200 && status != 206) return false;
            InputStream input = connection.getInputStream();
            try { return input.read() == 'P' && input.read() == 'K'; } finally { input.close(); }
        } finally {
            connection.disconnect();
        }
    }

    private static String fetchText(String address, int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setRequestProperty("User-Agent", "srceng-launcher-update-checker");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new Exception("HTTP " + status);
            InputStream input = connection.getInputStream();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
                StringBuilder text = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) text.append(line).append('\n');
                return text.toString().trim();
            } finally {
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }
}
