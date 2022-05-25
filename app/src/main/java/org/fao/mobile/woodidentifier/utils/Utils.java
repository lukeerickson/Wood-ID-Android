package org.fao.mobile.woodidentifier.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import org.fao.mobile.woodidentifier.Constants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;

public class Utils {
    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final int REQUEST_CAMERA = 2;
    private static final int REQUEST_FINE_LOCATION = 3;

    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    /**
     * Checks if the app has permission to write to device storage
     * <p>
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    public static void verifyLocationPermissions(Activity activity) {
        if (ActivityCompat.checkSelfPermission(activity,  Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_LOCATION,
                    REQUEST_FINE_LOCATION
            );
        }
    }

    @SuppressLint("LongLogTag")
    public static String assetFilePath(Context context, String assetName) {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e(Constants.TAG, "Error process asset " + assetName + " to file path");
        }
        return null;
    }

    public static Integer[] topK(double[] a, final int topk) {
        double[] values = new double[topk];
        Arrays.fill(values, -Double.MAX_VALUE);
        Integer[] ixs  = new Integer[topk];
        Arrays.fill(ixs, -1);

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < topk; j++) {
                if (a[i] > values[j]) {
                    for (int k = topk - 1; k >= j + 1; k--) {
                        values[k] = values[k - 1];
                        ixs[k] = ixs[k - 1];
                    }
                    values[j] = a[i];
                    ixs[j] = i;
                    break;
                }
            }
        }
        return ixs;
    }

    public static Integer[] topK(Double[] a, final int topk) {
        double buf[] = new double[topk];
        for(int i = 0; i < topk; i++) {
            buf[i] = a[i];
        }
        return Utils.topK(buf, topk);
    }

    public static Integer[] topK(float[] a, final int topk) {
        double buf[] = new double[topk];
        for(int i = 0; i < topk; i++) {
            buf[i] = a[i];
        }
        return Utils.topK(buf, topk);
    }

    public static void verifyCameraPermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_CAMERA,
                    REQUEST_CAMERA
            );
        }
    }

    public static String showArray(int data[]) {
        StringBuilder stringBuilder = new StringBuilder();
        int counter = 0;
        for (int d : data) {
            stringBuilder.append(d);
            if (counter < data.length - 1) {
                stringBuilder.append(",");
            }
            counter++;
        }
        return stringBuilder.toString();
    }

    public static <T> String showArray(T data[]) {
        StringBuilder stringBuilder = new StringBuilder();
        int counter = 0;
        for (T d : data) {
            stringBuilder.append(d);
            if (counter < data.length - 1) {
                stringBuilder.append(",");
            }
            counter++;
        }
        return stringBuilder.toString();
    }

    public static String timestampToString(long timestamp) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(new Date(timestamp).toInstant(), ZoneId.systemDefault());
        return localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static String readFileToStringSimple(File f) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (FileReader fileReader = new FileReader(f);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {

            while (bufferedReader.ready()) {
                stringBuilder.append(bufferedReader.readLine() + "\n");
            }
            return stringBuilder.toString();
        }
    }
}

