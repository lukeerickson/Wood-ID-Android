package org.fao.mobile.woodidentifier.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefsUtil {

    public static final String APP_SETTINGS = "app_settings";
    public static final String FIRST_RUN = "first_run";
    public static final String CURRENT_CAMERA = "current_camera";
    public static final String ZOOM = "zoom";

    public static boolean isFirstRun(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);
        return prefs.getBoolean(FIRST_RUN, true);
    }

    public static void setFirstRun(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(FIRST_RUN, false).commit();
    }

    public static int getCurrentCamera(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);

        return prefs.getInt(CURRENT_CAMERA, 0);
    }

    public static void saveCurrentCamera(Context context, int cameraId) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);
        prefs.edit().putInt(CURRENT_CAMERA, cameraId);
    }
}
