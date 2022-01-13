package org.fao.mobile.woodidentifier.utils;

import static androidx.core.math.MathUtils.clamp;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.params.RggbChannelVector;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import org.fao.mobile.woodidentifier.BaseCamera2Activity;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ImageUtils {
    private static final String TAG = ImageUtils.class.getCanonicalName();

    public static long colorTempFromRgb(float r, float g, float b) {
        float X = -0.14282f * r + 1.54924f * g + -0.95641f * b;
        float Y = -0.32466f * r + 1.57837f * g + -0.73191f * b;
        float Z = -0.68202f * r + 0.77073f * g + 0.56332f * b;
        float norm_x= X/(X+Y+Z);
        float norm_y=Y/(X+Y+Z);
        float n =(norm_x- 0.3320f)/(0.1858f-norm_y);
        return Math.round( 449f * Math.pow(n ,3f)+ 3525f * Math.pow(n,2f)+ 6823.3f * n + 5520.33f);
    }

    public static RggbChannelVector colorTemperature(int whiteBalance) {
        float temperature = whiteBalance / 100f;
        float red;
        float green;
        float blue;

        //Calculate red
        if (temperature <= 66)
            red = 255;
        else {
            red = temperature - 60;
            red = (float) (329.698727446 * (Math.pow((double) red, -0.1332047592)));
            if (red < 0)
                red = 0;
            if (red > 255)
                red = 255;
        }


        //Calculate green
        if (temperature <= 66) {
            green = temperature;
            green = (float) (99.4708025861 * Math.log(green) - 161.1195681661);
            if (green < 0)
                green = 0;
            if (green > 255)
                green = 255;
        } else {
            green = temperature - 60;
            green = (float) (288.1221695283 * (Math.pow((double) green, -0.0755148492)));
            if (green < 0)
                green = 0;
            if (green > 255)
                green = 255;
        }

        //calculate blue
        if (temperature >= 66)
            blue = 255;
        else if (temperature <= 19)
            blue = 0;
        else {
            blue = temperature - 10;
            blue = (float) (138.5177312231 * Math.log(blue) - 305.0447927307);
            if (blue < 0)
                blue = 0;
            if (blue > 255)
                blue = 255;
        }

        Log.i(TAG, "gains for " + whiteBalance + "red=" + red + ", green=" + green + ", blue=" + blue);
        return new RggbChannelVector((red / 255) * 2, (green / 255), (green / 255), (blue / 255) * 2);
    }

    public static Uri getPhotoFileUri(Context context, String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } else {
            File mediaStorageDir = null;
            try {
                mediaStorageDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM).getCanonicalPath());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
                Log.e(TAG, "failed to create directory");
            }
            File image = new File(mediaStorageDir, fileName);
            return Uri.fromFile(image);
        }
    }

    public static float[] computeAWBDelta(Image image) {
        float avgR = 0.0f;
        float avgG = 0.0f;
        float avgB = 0.0f;

        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Invalid image format");
        }

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        // ARGB array needed by Bitmap static factory method I use below.
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        yBuffer.position(0);

        // A YUV Image could be implemented with planar or semi planar layout.
        // A planar YUV image would have following structure:
        // YYYYYYYYYYYYYYYY
        // ................
        // UUUUUUUU
        // ........
        // VVVVVVVV
        // ........
        //
        // While a semi-planar YUV image would have layout like this:
        // YYYYYYYYYYYYYYYY
        // ................
        // UVUVUVUVUVUVUVUV   <-- Interleaved UV channel
        // ................
        // This is defined by row stride and pixel strides in the planes of the
        // image.

        // Plane 1 is always U & plane 2 is always V
        // https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        uBuffer.position(0);
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
        vBuffer.position(0);

        // The U/V planes are guaranteed to have the same row stride and pixel
        // stride.
        int yRowStride = image.getPlanes()[0].getRowStride();
        int yPixelStride = image.getPlanes()[0].getPixelStride();
        int uvRowStride = image.getPlanes()[1].getRowStride();
        int uvPixelStride = image.getPlanes()[1].getPixelStride();

        int r, g, b;
        int yValue, uValue, vValue;

        for (int y = 0; y < imageHeight; ++y) {
            for (int x = 0; x < imageWidth; ++x) {
                int yIndex = (y * yRowStride) + (x * yPixelStride);
                // Y plane should have positive values belonging to [0...255]
                yValue = (yBuffer.get(yIndex) & 0xff);

                int uvx = x / 2;
                int uvy = y / 2;
                // U/V Values are subsampled i.e. each pixel in U/V chanel in a
                // YUV_420 image act as chroma value for 4 neighbouring pixels
                int uvIndex = (uvy * uvRowStride) + (uvx * uvPixelStride);

                // U/V values ideally fall under [-0.5, 0.5] range. To fit them into
                // [0, 255] range they are scaled up and centered to 128.
                // Operation below brings U/V values to [-128, 127].
                uValue = (uBuffer.get(uvIndex) & 0xff) - 128;
                vValue = (vBuffer.get(uvIndex) & 0xff) - 128;

                // Compute RGB values per formula above.
                r = (int) (yValue + 1.370705f * vValue);
                g = (int) (yValue - (0.698001f * vValue) - (0.337633f * uValue));
                b = (int) (yValue + 1.732446f * uValue);
                r = clamp(r, 0, 255);
                g = clamp(g, 0, 255);
                b = clamp(b, 0, 255);

                avgB += b;
                avgG += g;
                avgR += r;
            }
        }
        float totalCells = imageHeight * imageWidth;
        Log.i(TAG, avgR + "," + avgG + "," +avgB + " total " + totalCells);
        return new float[]{ avgR / totalCells, avgG / totalCells, avgB / totalCells};
    }

}
