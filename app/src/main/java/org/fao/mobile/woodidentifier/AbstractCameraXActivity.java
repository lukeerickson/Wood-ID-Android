package org.fao.mobile.woodidentifier;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.DragEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExposureState;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.room.Room;

import com.google.android.material.slider.Slider;
import com.google.common.util.concurrent.ListenableFuture;

import org.fao.mobile.woodidentifier.models.InferencesLog;
import org.fao.mobile.woodidentifier.utils.ModelHelper;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public abstract class AbstractCameraXActivity extends AppCompatActivity implements View.OnClickListener, Slider.OnChangeListener {
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 200;
    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA};
    private static final String TAG = AbstractCameraXActivity.class.getCanonicalName();
    private static final int IMAGE_CAPTURE = 101;


    private long mLastAnalysisResultTime;
    private View takePicture;
    private ImageCapture imageCapture;
    private Camera camera;
    private Slider zoomControl;
    private Slider exposureControl;

    protected abstract int getContentViewLayoutId();

    protected abstract PreviewView getCameraPreviewTextureView();

    protected HandlerThread mBackgroundThread;
    protected Handler mBackgroundHandler;
    protected Handler mUIHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutId());
        this.takePicture = findViewById(R.id.fab_take_picture);
        this.zoomControl = (Slider) findViewById(R.id.zoom_control);
        this.exposureControl = (Slider) findViewById(R.id.exposure_control);

        setupDefaultValues();

        takePicture.setOnClickListener(this);
        zoomControl.addOnChangeListener(this);
        exposureControl.addOnChangeListener(this);
        startBackgroundThread();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS,
                    REQUEST_CODE_CAMERA_PERMISSION);
        } else {
            setupCameraX();
        }
    }

    private void setupDefaultValues() {
        SharedPreferences prefs = this.getSharedPreferences(
                "camera_settings", Context.MODE_PRIVATE);
        zoomControl.setValue(prefs.getFloat("zoom", (float) 0f));
        exposureControl.setValue(prefs.getFloat("exposure", 0f));
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        startBackgroundThread();
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("ModuleActivity");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    protected void onDestroy() {
        stopBackgroundThread();
        super.onDestroy();
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Error on stopping background thread", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(
                        this,
                        "You can't use image classification example without granting CAMERA permission",
                        Toast.LENGTH_LONG)
                        .show();
                finish();
            } else {
                setupCameraX();
            }
        }
    }

    private void setupCameraX() {
        final PreviewView textureView = getCameraPreviewTextureView();

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                final Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(textureView.getSurfaceProvider());
                Size size = new Size(512, 512);
                this.imageCapture =
                        new ImageCapture.Builder()
                                .setTargetRotation(textureView.getDisplay().getRotation())
                                .setTargetResolution(size)
                                .build();
                cameraProvider.unbindAll();
                this.camera = cameraProvider.bindToLifecycle(AbstractCameraXActivity.this, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture, preview);

                SharedPreferences prefs = this.getSharedPreferences(
                        "camera_settings", Context.MODE_PRIVATE);
                setCameraZoom(prefs.getFloat("zoom", (float) 0f));
                setCameraExposure(prefs.getFloat("exposure", 0f));
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @WorkerThread
    @Nullable
    protected abstract ModelHelper.Result analyzeImage(ImageProxy image, int rotationDegrees);

    @UiThread
    protected abstract void applyToUiAnalyzeImageResult(ModelHelper.Result result);

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab_take_picture:
                DateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddhhmmss");
                File file = getPhotoFileUri("capture_" + simpleDateFormat.format(new Date()) + ".jpg");

                ImageCapture.OutputFileOptions outputFileOptions =
                        new ImageCapture.OutputFileOptions.Builder(file).build();

                imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this),
                        new ImageCapture.OnImageSavedCallback() {
                            @Override
                            public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                                Intent intent = new Intent();
                                intent.setData(Uri.fromFile(file));
                                AbstractCameraXActivity.this.setResult(Activity.RESULT_OK, intent);
                                AbstractCameraXActivity.this.finish();
                            }

                            @Override
                            public void onError(ImageCaptureException error) {
                                // insert your code here.
                                Log.e(TAG, error.getMessage());
                            }
                        }
                );

                break;
        }
    }

    public File getPhotoFileUri(String fileName) {
        // Get safe storage directory for photos
        // Use `getExternalFilesDir` on Context to access package-specific directories.
        // This way, we don't need to request external read/write runtime permissions.
        File mediaStorageDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), TAG);

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.e(TAG, "failed to create directory");
        }

        // Return the file target for the photo based on filename
        File file = new File(mediaStorageDir.getPath() + File.separator + fileName);

        return file;
    }


    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void onValueChange(Slider slider, float value, boolean fromUser) {
        float sliderValue = slider.getValue();
        if (slider.getId() == R.id.zoom_control) {
            setCameraZoom(sliderValue);
            SharedPreferences prefs = this.getSharedPreferences(
                    "camera_settings", Context.MODE_PRIVATE);
            prefs.edit().putFloat("zoom", sliderValue).commit();
        } else if (slider.getId() == R.id.exposure_control) {
            setCameraExposure(sliderValue);
            SharedPreferences prefs = this.getSharedPreferences(
                    "camera_settings", Context.MODE_PRIVATE);
            prefs.edit().putFloat("exposure", sliderValue).commit();
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void setCameraExposure(float sliderValue) {
        ExposureState exposureState = camera.getCameraInfo().getExposureState();
        Range<Integer> range = exposureState.getExposureCompensationRange();
        int exposureRange = (int) ((range.getUpper() - range.getLower()) * sliderValue + range.getLower());
        camera.getCameraControl().setExposureCompensationIndex(exposureRange);
    }

    private void setCameraZoom(float sliderValue) {
        LiveData<ZoomState> zoomState = camera.getCameraInfo().getZoomState();
        ZoomState zoom = zoomState.getValue();
        float zoomRatio = (zoom.getMaxZoomRatio() - zoom.getMinZoomRatio()) * sliderValue + zoom.getMinZoomRatio();
        Log.d(TAG, "changing zoom to " + zoomRatio);
        camera.getCameraControl().setZoomRatio(zoomRatio);
    }
}
