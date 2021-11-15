package org.fao.mobile.woodidentifier.utils;

import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.AE_COMPENSATION;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CROP_FACTOR;
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
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(ZOOM, Double.toString(zoomRatio));
                editor.putString(WHITE_BALANCE, Integer.toString(whiteBalance));
                editor.putString(AE_COMPENSATION, Integer.toString(aeCompensation));
                editor.putString(CROP_FACTOR, Integer.toString(cropFactor));
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
