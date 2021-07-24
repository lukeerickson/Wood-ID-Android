package org.fao.mobile.woodidentifier.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefsUtil {

    public static final String APP_SETTINGS = "app_settings";

    public static boolean isFirstRun(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);
        return prefs.getBoolean("first_run", true);
    }

    public static void setFirstRun(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean("first_run", false).commit();
    }
}
