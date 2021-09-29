package org.fao.mobile.woodidentifier.utils;

import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.ZOOM;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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
                double zoomRatio = autoPhoneSettings.getDouble("zoom");
                int whiteBalance = autoPhoneSettings.getInt("white_balance");
                int aeCompensation = autoPhoneSettings.getInt("ae_compensation");

                SharedPreferences prefs = context.getSharedPreferences(
                        "camera_settings", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putFloat(ZOOM, (float) zoomRatio);
                editor.putInt("white_balance", whiteBalance);
                editor.putInt("ae_compensation", aeCompensation);
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
