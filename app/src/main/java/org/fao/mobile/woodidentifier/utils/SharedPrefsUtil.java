package org.fao.mobile.woodidentifier.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.util.JsonWriter;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import org.fao.mobile.woodidentifier.MainActivity;
import org.fao.mobile.woodidentifier.models.User;
import org.json.JSONObject;

public class SharedPrefsUtil {

    public static final String APP_SETTINGS = "app_settings";
    public static final String FIRST_RUN = "first_run";
    public static final String CURRENT_CAMERA = "current_camera";
    public static final String ZOOM = "zoom";
    public static final String WHITE_BALANCE = "white_balance";
    public static final String AE_COMPENSATION = "ae_compensation";
    public static final String LOCATION_TAGGING = "location_tagging";
    public static final String CROP_FACTOR = "crop_factor";
    public static final String CUSTOM_AWB = "enable_custom_awb";
    public static final String CUSTOM_AWB_VALUES = "custom_awb_gains";
    public static final String CROP_X = "crop_x";
    public static final String CROP_Y = "crop_y";
    private static final String DEVELOPMENT_MODE = "developer_mode";
    private static final String APP_USER = "user";
    private static final String CURRENT_LOCATION = "current_location";
    public static final String SENSOR_SENSITIVITY = "sensor_sensitivity";
    public static final String FRAME_DURATION_TIME = "frame_duration_time";
    public static final String EXPOSURE_TIME = "exposure_time";
    public static final String USE_CUSTOM_EXPOSURE = "use_custom_exposure";
    public static final String PIN_SECURITY = "enable_pin_code";
    public static final String ACCURACY_THRESHOLD = "accuracy_threshold";

    public static boolean isDeveloperMode(Context context) {
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return defaultPrefs.getBoolean(DEVELOPMENT_MODE, false);
    }

    public static boolean isFirstRun(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);
        return prefs.getBoolean(FIRST_RUN, true);
    }
    
    public static boolean enablePinSecurity(Context context) {
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return defaultPrefs.getBoolean(PIN_SECURITY, true);
    }

    public static boolean isCustomAWB(Context context) {
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return defaultPrefs.getBoolean(CUSTOM_AWB, false);
    }

    public static boolean isCustomExposure(Context context) {
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return defaultPrefs.getBoolean(USE_CUSTOM_EXPOSURE, false);
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

    public static User getUserInfo(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);
        String userInfoJson = prefs.getString(APP_USER, null);
        if (userInfoJson != null) {
            return new Gson().fromJson(userInfoJson, User.class);
        } else {
            return null;
        }
    }

    public static void saveUserInfo(Context context, User user) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);
        prefs.edit().putString(APP_USER, new Gson().toJson(user)).commit();
    }

    public static void saveCurrentCamera(Context context, int cameraId) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);
        prefs.edit().putInt(CURRENT_CAMERA, cameraId).commit();
    }

    public static boolean isLoggedIn() {
        return false;
    }

    public static void setCurrentLocation(Context context, String location) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);
        prefs.edit().putString(CURRENT_LOCATION, location).commit();
    }

    public static String getCurrentLocation(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                APP_SETTINGS, Context.MODE_PRIVATE);
        return prefs.getString(CURRENT_LOCATION, "");
    }

    public static float accuracyThreshold(Activity context) {
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return defaultPrefs.getFloat(ACCURACY_THRESHOLD, 4.0f);
    }
}
