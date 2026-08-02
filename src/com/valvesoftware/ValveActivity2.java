package com.valvesoftware;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import java.util.HashMap;
import java.io.File;
import java.util.Locale;
import org.libsdl.app.SDLActivity;
import me.nillerusr.LauncherActivity;
import android.content.SharedPreferences;
import android.content.Context;
import android.util.Log;
import me.nillerusr.ExtractAssets;
import me.nillerusr.md3.Md3Theme;

public class ValveActivity2 { // not activity, i am lazy to change native methods
	private static Activity mSingleton;
	public static SharedPreferences mPref;


	public static native void setArgs(String args);
	public static native int setenv(String name, String value, int overwrite);
	private static native void nativeOnActivityResult(Activity activity, int i, int i2, Intent intent);

	public static int findGameinfo(String path)
	{
		File dir = new File(path);
		boolean havePlatform = false;
		boolean haveGameinfo = false;

		if( !dir.isDirectory() )
			return 0;

		for( File file : dir.listFiles() )
		{
			if( file.isDirectory() )
			{
				for( File f : file.listFiles() )
				{
					if( f.getName().toLowerCase().equals("gameinfo.txt") )
						haveGameinfo = true;
				}
			}

			if( file.getName().toLowerCase().equals("platform") )
				havePlatform = true;
		}

		if( !haveGameinfo )
			return 0;

		if( !havePlatform )
			return -1;

		return 1;
	}

	static public boolean isModGameinfoExists(String path)
	{
		File dir = new File(path);
		if( !dir.isDirectory() )
			return false;

		for( File file : dir.listFiles() )
		{
			if( file.isFile() && file.getName().toLowerCase().equals("gameinfo.txt") )
				return true;
		}

		return false;
	}

	static public int preInit(Context context, Intent intent)
	{
		mPref = context.getSharedPreferences("mod", 0);
		String gamepath = mPref.getString("gamepath", LauncherActivity.getDefaultDir() + "/srceng");
		String gamedir = intent.getStringExtra("gamedir");
		if( gamedir == null || gamedir.isEmpty() )
			gamedir = "hl2";

		int haveGameinfo = findGameinfo(gamepath);
		boolean haveModGameinfo = isModGameinfoExists(gamepath+"/"+gamedir);

		if( haveGameinfo == 0 || !haveModGameinfo )
			return 0;

		if( haveGameinfo == -1 )
			return -1;

		return 1;
	}

	static public void initNatives(Context context, Intent intent) {
		mPref = context.getSharedPreferences("mod", 0);
		ApplicationInfo appinf = context.getApplicationInfo();
		String gamepath = mPref.getString("gamepath", LauncherActivity.getDefaultDir() + "/srceng");

		String argv = intent.getStringExtra("argv");
		String gamedir = intent.getStringExtra("gamedir");
		String gamelibdir = intent.getStringExtra("gamelibdir");
		String customVPK = intent.getStringExtra("vpk");
		Log.v("SRCAPK", "argv="+argv);

		if( gamedir == null || gamedir.isEmpty() )
			gamedir = "hl2";

		if( argv == null || argv.isEmpty() )
			argv = mPref.getString("argv", "-nobackgroundlevel");

		argv = "-game "+gamedir+" "+argv;

		// Append "-language <lang>" transparently from launcher settings (MD3).
		// Empty means: don't force, let the game decide from Steam/LANG env.
		String gameLang = Md3Theme.getGameLang(context);
		if( gameLang != null && !gameLang.isEmpty() ) {
			// Sanitize: only allow [a-z_] to prevent argv injection
			if( gameLang.matches("[a-z_]+") ) {
				argv = argv + " -language " + gameLang;
			}
		}

		// Append resolution args: -w <width> -h <height> + -full / -windowed
		// DEVICE mode (0,0) means: don't add -w/-h, let engine use device native res.
		int[] res = Md3Theme.getResolvedResolution(context);
		if( res != null && res.length >= 2 && res[0] > 0 && res[1] > 0 ) {
			int w = res[0], h = res[1];
			// Sanitize: reasonable range (320..8192 for both)
			if( w >= 320 && w <= 8192 && h >= 240 && h <= 8192 ) {
				argv = argv + " -w " + w + " -h " + h;
			}
		}
		// Fullscreen / windowed
		boolean fullscreen = Md3Theme.getResolutionFullscreen(context);
		argv = fullscreen ? (argv + " -full") : (argv + " -windowed");

		if( gamelibdir != null && !gamelibdir.isEmpty() )
			setenv( "APP_MOD_LIB", gamelibdir, 1 );

		ExtractAssets.extractAssets(context);

		String vpks = context.getFilesDir().getPath()+"/"+ExtractAssets.VPK_NAME;
		if( customVPK != null && !customVPK.isEmpty() )
			vpks = customVPK+","+vpks;

		Log.v("SRCAPK", "vpks="+vpks);

		setenv( "EXTRAS_VPK_PATH", vpks, 1 );
		setenv( "LANG", Locale.getDefault().toString(), 1 );
		setenv( "APP_DATA_PATH", appinf.dataDir, 1);
		setenv( "APP_LIB_PATH", appinf.nativeLibraryDir, 1);

		if (mPref.getBoolean("rodir", false))
			setenv( "VALVE_GAME_PATH", LauncherActivity.getAndroidDataDir(), 1 );
		else
			setenv( "VALVE_GAME_PATH", gamepath, 1 );

		Log.v("SRCAPK", "argv="+argv);
		setArgs(argv);
	}
}
