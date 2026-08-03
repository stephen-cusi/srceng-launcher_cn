package me.nillerusr;
import android.content.SharedPreferences;
import java.io.FileOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import android.util.Log;
import android.content.Context;
import android.content.pm.ApplicationInfo;

public class ExtractAssets
{
	public static String TAG = "ExtractAssets";
	static SharedPreferences mPref;

	public static final String VPK_NAME = "extras_dir.vpk";
	public static int PAK_VERSION = 24;

    private static int chmod(String path, int mode)
    {
		int ret = -1;

		try
		{
			ret = Runtime.getRuntime().exec("chmod " + Integer.toOctalString(mode) + " " + path).waitFor();
			Log.d(TAG, "chmod " + Integer.toOctalString(mode) + " " + path + ": " + ret );
		}
		catch(Exception e)
		{
			ret = -1;
			Log.d(TAG, "chmod: Runtime not worked: " + e.toString() );
		}

		try
		{
			Class fileUtils = Class.forName("android.os.FileUtils");
			Method setPermissions = fileUtils.getMethod("setPermissions", String.class, int.class, int.class, int.class);
			ret = (Integer) setPermissions.invoke(null, path, mode, -1, -1);
		}
		catch(Exception e)
		{
			ret = -1;
			Log.d(TAG, "chmod: FileUtils not worked: " + e.toString() );
		}

		return ret;
	}

	public static boolean extractAsset(Context context, String asset, Boolean force)
	{
		android.content.res.AssetManager am = context.getAssets();
		File asset_file = new File(context.getFilesDir(), asset);
		File tmp = new File(context.getFilesDir(), asset + ".tmp");
		File backup = new File(context.getFilesDir(), asset + ".bak");
		InputStream is = null;
		FileOutputStream os = null;
		try {
			String asset_path = asset_file.getPath();
			Boolean asset_exists = asset_file.exists();

			if( !force && asset_exists )
				return true;

			long written = 0;
			is = am.open(asset);
			os = new FileOutputStream(tmp);
			byte[] buffer = new byte[8192];
			while (true) {
				int length = is.read(buffer);
				if (length <= 0)
					break;
				os.write(buffer, 0, length);
				written += length;
			}
			os.getFD().sync();
			os.close();
			os = null;
			is.close();
			is = null;
			if( written <= 0 || tmp.length() != written )
				throw new java.io.IOException("Incomplete asset copy");
			if( backup.exists() && !backup.delete() )
				throw new java.io.IOException("Failed to remove stale asset backup");
			if( asset_exists && !asset_file.renameTo(backup) )
				throw new java.io.IOException("Failed to preserve existing asset");
			if( !tmp.renameTo(asset_file) ) {
				if( backup.exists() ) backup.renameTo(asset_file);
				throw new java.io.IOException("Failed to install extracted asset");
			}
			if( backup.exists() ) backup.delete();
			chmod(asset_path, 0777);
			return true;
		}
		catch (Exception e) {
			if( !asset_file.exists() && backup.exists() && !backup.renameTo(asset_file) )
				Log.e("SRCAPK", "Failed to restore previous " + asset);
			Log.e("SRCAPK", "Failed to extract " + asset + ":" + e.toString());
			return false;
		}
		finally {
			try { if( os != null ) os.close(); } catch (Exception ignore) {}
			try { if( is != null ) is.close(); } catch (Exception ignore) {}
			if( tmp.exists() ) tmp.delete();
		}
	}

	public static void extractAssets(Context context)
	{
		ApplicationInfo appinf = context.getApplicationInfo();
		chmod(appinf.dataDir, 0777);
		chmod(context.getFilesDir().getPath(), 0777);

		extractVPK(context);
		extractAsset(context, "DroidSansFallback.ttf", false);
		extractAsset(context, "LiberationMono-Regular.ttf", false);
		extractAsset(context, "dejavusans-boldoblique.ttf", false);
		extractAsset(context, "dejavusans-bold.ttf", false);
		extractAsset(context, "dejavusans-oblique.ttf", false);
		extractAsset(context, "dejavusans.ttf", false);
		extractAsset(context, "Itim-Regular.otf", false);
	}

	public static void extractVPK(Context context)
	{
		if( mPref == null )
			mPref = context.getSharedPreferences("mod", 0);

		int version = mPref.getInt( "pakversion", 0 );
		Boolean force = (version != PAK_VERSION);

		if( extractAsset(context, VPK_NAME, force) )
			mPref.edit().putInt( "pakversion", PAK_VERSION ).apply();
	}

	// Old API kept for compatibility (not used anymore in 1.17)
	@Deprecated
	public static void extractVPK(Context context, Boolean force)
	{
		extractAssets(context);
	}
}
