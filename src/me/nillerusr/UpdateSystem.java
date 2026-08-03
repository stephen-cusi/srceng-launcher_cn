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

public class UpdateSystem extends AsyncTask<Void, Void, UpdateSystem.Result> {
    public static final String CHANNEL_STABLE = "stable";
    public static final String CHANNEL_DEV = "dev";
    public static final String PREF_CHANNEL = "update_channel";
    private static final String RAW_BASE = "https://raw.githubusercontent.com/stephen-cusi/srceng-launcher-updates/main/";

    public interface Callback {
        void onUpdateResult(Result result);
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
    private final Callback callback;

    public UpdateSystem(Context context, String channel, Callback callback) {
        this.context = context.getApplicationContext();
        this.channel = CHANNEL_DEV.equals(channel) ? CHANNEL_DEV : CHANNEL_STABLE;
        this.callback = callback;
    }

    @Override
    protected Result doInBackground(Void... ignored) {
        Result result = new Result();
        try {
            JSONObject manifest = new JSONObject(fetchText(RAW_BASE + channel + "/manifest.json"));
            if (!manifest.optBoolean("published", true)) {
                result.success = true;
                result.published = false;
                result.available = false;
                return result;
            }
            result.published = true;
            result.versionName = manifest.getString("versionName");
            result.build = manifest.getInt("build");
            result.apkUrl = manifest.getString("apkUrl");
            result.changelogUrl = manifest.getString("changelogUrl");
            result.sha256 = manifest.optString("sha256", "");
            result.changelog = fetchText(result.changelogUrl);

            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            int localBuild = context.getResources().getInteger(com.valvesoftware.source.R.integer.update_build);
            result.available = !result.versionName.equals(info.versionName) && result.build > localBuild;
            result.success = true;
        } catch (Exception e) {
            result.error = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        return result;
    }

    @Override
    protected void onPostExecute(Result result) {
        if (callback != null) callback.onUpdateResult(result);
    }

    private static String fetchText(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
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
