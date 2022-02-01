package org.fao.mobile.woodidentifier.utils;

import static android.text.TextUtils.isEmpty;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.AE_COMPENSATION;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CROP_FACTOR;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CUSTOM_AWB;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CUSTOM_AWB_VALUES;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.EXPOSURE_TIME;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.FRAME_DURATION_TIME;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.SENSOR_SENSITIVITY;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.USE_CUSTOM_EXPOSURE;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.WHITE_BALANCE;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.ZOOM;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class PhoneAutoConfig {

    private static final String PHONE_DATABASE = "phone_database.json";
    private static final String TAG = PhoneAutoConfig.class.getCanonicalName();


    public static boolean setPhoneSettingsFor(Context context, String phoneID) {
        String assetPath = Utils.assetFilePath(context,
                PHONE_DATABASE);
        assert assetPath != null;
        try {
            JSONObject jObject = new JSONObject(Utils.readFileToStringSimple(new File(assetPath)));
            if (jObject.has(phoneID)) {
                JSONObject autoPhoneSettings = jObject.getJSONObject(phoneID);
                Log.i(TAG, "autoconfig " + autoPhoneSettings.toString());
                double zoomRatio = autoPhoneSettings.getDouble(ZOOM);
                int whiteBalance = autoPhoneSettings.getInt(WHITE_BALANCE);
                int aeCompensation = autoPhoneSettings.getInt(AE_COMPENSATION);
                int cropFactor = autoPhoneSettings.getInt(CROP_FACTOR);
                long sensitivity = autoPhoneSettings.getLong(SENSOR_SENSITIVITY);
                long exposure_time = autoPhoneSettings.getLong(EXPOSURE_TIME);
                long frame_duration = autoPhoneSettings.getLong(FRAME_DURATION_TIME);
                boolean customExposureSettings = autoPhoneSettings.optBoolean("custom_exposure", false);
                String awbSettings = autoPhoneSettings.optString(CUSTOM_AWB_VALUES, "255.0,255.0,255.0");
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(ZOOM, Double.toString(zoomRatio));
                editor.putString(WHITE_BALANCE, Integer.toString(whiteBalance));
                editor.putString(AE_COMPENSATION, Integer.toString(aeCompensation));
                editor.putString(CROP_FACTOR, Integer.toString(cropFactor));
                editor.putString(CUSTOM_AWB_VALUES, awbSettings);
                if (customExposureSettings) {
                    editor.putBoolean(USE_CUSTOM_EXPOSURE, true);
                    editor.putString(SENSOR_SENSITIVITY, Long.toString(sensitivity));
                    editor.putString(EXPOSURE_TIME, Long.toString(exposure_time));
                    editor.putString(FRAME_DURATION_TIME, Long.toString(frame_duration));
                } else {
                    editor.putBoolean(USE_CUSTOM_EXPOSURE, false);
                }
                editor.putBoolean(CUSTOM_AWB, !isEmpty(awbSettings));
                editor.commit();
                return true;
            } else {
                Log.e(TAG, "cannot auto configure for phone" + phoneID);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return false;
    }
}
