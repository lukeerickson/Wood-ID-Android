package org.fao.mobile.woodidentifier;

import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.ZOOM;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.DragEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public abstract class AbstractCameraXActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 200;
    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA};
    private static final String TAG = AbstractCameraXActivity.class.getCanonicalName();
    private static final int IMAGE_CAPTURE = 101;


    private long mLastAnalysisResultTime;
    private View takePicture;
    private ImageCapture imageCapture;
    private Camera camera;

    protected abstract int getContentViewLayoutId();

    protected abstract PreviewView getCameraPreviewTextureView();

    protected HandlerThread mBackgroundThread;
    protected Handler mBackgroundHandler;
    protected Handler mUIHandler;

    protected abstract void afterOnCreate();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutId());
        afterOnCreate();
        this.takePicture = findViewById(R.id.fab_take_picture);
        setupDefaultValues();

        takePicture.setOnClickListener(this);

        startBackgroundThread();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS,
                    REQUEST_CODE_CAMERA_PERMISSION);
        } else {
            try {
                setupCameraX();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupDefaultValues() {

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
                try {
                    setupCameraX();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void setupCameraX() throws CameraAccessException {
        final PreviewView textureView = getCameraPreviewTextureView();
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String[] cameras = manager.getCameraIdList();
        Log.i(TAG, "number of cameras " + cameras.length);

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
                setCameraZoom(prefs.getFloat(ZOOM, (float) 0f));
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
                Uri fileUri = getPhotoFileUri("capture_" + simpleDateFormat.format(new Date()) + ".jpg");
                ImageCapture.Metadata metadata = new ImageCapture.Metadata();
                OutputStream outputStream = null;
                try {
                    outputStream = getContentResolver().openOutputStream(fileUri);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                ImageCapture.OutputFileOptions outputFileOptions =
                        new ImageCapture.OutputFileOptions.Builder(outputStream).setMetadata(metadata).build();

                imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this),
                        new ImageCapture.OnImageSavedCallback() {
                            @Override
                            public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                                Intent intent = new Intent();
                                intent.setData(fileUri);
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

    public Uri getPhotoFileUri(String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            return uri;
        } else {
            File mediaStorageDir = new File(getExternalFilesDir(Environment.DIRECTORY_DCIM), TAG);

            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
                Log.e(TAG, "failed to create directory");
            }
            File image = new File(mediaStorageDir, fileName);
            return Uri.fromFile(image);
        }
    }


    @SuppressLint("UnsafeOptInUsageError")
    protected void setCameraExposure(float sliderValue) {
        ExposureState exposureState = camera.getCameraInfo().getExposureState();
        Range<Integer> range = exposureState.getExposureCompensationRange();
        int exposureRange = (int) ((range.getUpper() - range.getLower()) * sliderValue + range.getLower());
        camera.getCameraControl().setExposureCompensationIndex(exposureRange);
        afterExposureSet(sliderValue);

    }

    protected abstract void afterExposureSet(float value);

    protected void setCameraZoom(float sliderValue) {
        LiveData<ZoomState> zoomState = camera.getCameraInfo().getZoomState();
        ZoomState zoom = zoomState.getValue();
        float zoomRatio = (zoom.getMaxZoomRatio() - zoom.getMinZoomRatio()) * sliderValue + zoom.getMinZoomRatio();
        Log.d(TAG, "changing zoom to " + zoomRatio);
        camera.getCameraControl().setZoomRatio(zoomRatio);
        afterZoomSet(sliderValue);
    }

    protected abstract void afterZoomSet(float value);
}
