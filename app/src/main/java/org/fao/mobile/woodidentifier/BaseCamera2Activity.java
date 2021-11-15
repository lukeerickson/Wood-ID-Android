package org.fao.mobile.woodidentifier;

import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.AE_COMPENSATION;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.WHITE_BALANCE;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.ZOOM;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.fao.mobile.woodidentifier.utils.ImageUtils;
import org.fao.mobile.woodidentifier.utils.SharedPrefsUtil;
import org.fao.mobile.woodidentifier.utils.StringUtils;
import org.fao.mobile.woodidentifier.utils.Utils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseCamera2Activity extends AppCompatActivity {
    private static final String PHONE_DATABASE = "phone_database.json";
    private String TAG = BaseCamera2Activity.class.getCanonicalName();
    protected CameraProperties currentCameraCharacteristics;
    protected SurfaceHolder holder;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    protected CameraManager cameraManager;
    private SurfaceView viewFinder;
    protected CameraDevice currentCamera;
    protected CameraCaptureSession session;
    private View capureButton;
    private View cameraFrame;
    private ImageReader imageReader;
    private View cancelButton;

    @SuppressLint("MissingPermission")
    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        this.capureButton = getCaptureButton();
        this.cancelButton = getCancelButton();
        this.viewFinder = getCameraPreviewTextureView();
        this.cameraFrame = findViewById(R.id.cameraFrame);
        this.cameraManager = (CameraManager) getSystemService(Service.CAMERA_SERVICE);
        startBackgroundThread();

        viewFinder.getHolder().addCallback(new SurfaceHolder.Callback() {
            @RequiresApi(api = Build.VERSION_CODES.R)
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                BaseCamera2Activity.this.holder = holder;
                setupCamera(holder);
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
        });

        cancelButton.setOnClickListener((view) -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
    }

    protected abstract View getCaptureButton();

    protected abstract View getCancelButton();

    abstract SurfaceView getCameraPreviewTextureView();

    protected void startBackgroundThread() {
        HandlerThread mBackgroundThread = new HandlerThread("ModuleActivity");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void setupCamera(SurfaceHolder holder) {
        try {
            ArrayList<CameraProperties> backFacingCameras = enumerateBackCameras();
            int currentCamera = SharedPrefsUtil.getCurrentCamera(this);
            CameraProperties camera = backFacingCameras.get(currentCamera);

            // fix to maintain correct aspect ratio in screen

            runOnUiThread(() -> {
                Size size = camera.getSize();
                float heightRatio = (float) size.getWidth() / (float) size.getHeight();
                Log.d(TAG, "image size " + size.getWidth() + "," + size.getHeight() + " = " + heightRatio);
                Log.d(TAG, "new ratio " + cameraFrame.getWidth() + "," + (int) (cameraFrame.getWidth() * heightRatio));
                cameraFrame.setLayoutParams(new RelativeLayout.LayoutParams(cameraFrame.getWidth(), (int) (cameraFrame.getWidth() * heightRatio)));

                try {
                    setupCamera(holder, camera);
                    onSetupCameraComplete(backFacingCameras, camera);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            });
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void onSetupCameraComplete(ArrayList<CameraProperties> backFacingCameras, CameraProperties camera) throws CameraAccessException {

    }

    public static class CameraProperties {

        final String cameraId;
        final Integer sensorOrientation;
        final Range<Float> zoomRatioRange;
        final Range<Integer> aeCompensationRange;

        public String getCameraId() {
            return cameraId;
        }

        public Size getSize() {
            return size;
        }

        private final Size size;

        public CameraProperties(String cameraId, Size size, Range<Float> zoomRatioRange, Range<Integer> aeCompensationRange, Integer sensorOrientation) {
            this.cameraId = cameraId;
            this.size = size;
            this.zoomRatioRange = zoomRatioRange;
            this.aeCompensationRange = aeCompensationRange;
            this.sensorOrientation = sensorOrientation;
        }

        public Range<Float> getZoomRatioRange() {
            return zoomRatioRange;
        }

        public String toString() {
            return "Camera " + cameraId;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof CameraProperties) {
                CameraProperties targetObj = (CameraProperties) obj;
                return targetObj.cameraId.equals(cameraId);
            }
            return false;
        }
    }

    /**
     * Encode data about detected back facing cameras
     *
     * @return A list of camera properties
     * @throws CameraAccessException
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private ArrayList<CameraProperties> enumerateBackCameras() throws CameraAccessException {
        String[] cameraList = cameraManager.getCameraIdList();
        ArrayList<RecalibrateCameraActivity.CameraProperties> backFacingCameras = new ArrayList<RecalibrateCameraActivity.CameraProperties>();
        String phoneId = Build.MANUFACTURER + "-" + Build.MODEL;
        Log.i(TAG, "Phone ID " + phoneId);
        Log.i(TAG, "Total Cameras = " + cameraList.length);

        for (String cameraId : cameraList) {
            Log.i(TAG, "camera = " + cameraId);
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer cameraLensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                Size size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                Range<Float> zoomRatioRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                Range<Integer> aeCompensationRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (cameraLensFacing == CameraMetadata.LENS_FACING_BACK) {
                    Log.i(TAG, "camera " + cameraId + " is backfacing");
                    Log.i(TAG, "sensor orientation is " + sensorOrientation);
                    Log.i(TAG, "camera resolution " + size.getWidth() + "," + size.getHeight());
                    Size[] outputSizes = streamConfigurationMap.getOutputSizes(ImageReader.class);

                    int[] outputFormats = streamConfigurationMap.getOutputFormats();
                    long maxResolution = 0;
                    Size maxSize = size;
                    if (outputSizes != null) {
                        for (Size s : outputSizes) {
                            Log.i(TAG, "imageReader output resolution " + s.getWidth() + "," + s.getHeight());
                            if (maxResolution < s.getWidth() * s.getHeight()) {
                                maxResolution = s.getWidth() * s.getHeight();
                                maxSize = s;
                            }
                        }
                    }

                    List<String> outputFormatStr = Arrays.stream(outputFormats).mapToObj(i -> Integer.toString(i)).collect(Collectors.toList());
                    Log.i(TAG, "output formats " + StringUtils.joinAsJson(outputFormatStr));
                    Log.i(TAG, "zoom ratio " + zoomRatioRange.getLower() + " to " + zoomRatioRange.getUpper());
                    Log.i(TAG, "AE compensation range " + aeCompensationRange.getLower() + " to " + aeCompensationRange.getUpper());
                    RecalibrateCameraActivity.CameraProperties properties = new RecalibrateCameraActivity.CameraProperties(cameraId, maxSize, zoomRatioRange, aeCompensationRange, sensorOrientation);
                    backFacingCameras.add(properties);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        return backFacingCameras;
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.R)
    protected void setupCamera(SurfaceHolder holder, CameraProperties cameraProperties) throws CameraAccessException {
        this.currentCameraCharacteristics = cameraProperties;
        if (currentCamera != null) {
            currentCamera.close();
        }
        cameraManager.openCamera(cameraProperties.cameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                BaseCamera2Activity.this.currentCamera = camera;
                ArrayList<Surface> outputs = new ArrayList<>();
                imageReader = ImageReader.newInstance(cameraProperties.size.getWidth(), cameraProperties.size.getHeight(), ImageFormat.JPEG, 1);

                Surface surface = holder.getSurface();
                outputs.add(surface);
                outputs.add(imageReader.getSurface());
                try {
                    camera.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                        @RequiresApi(api = Build.VERSION_CODES.R)
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            Log.i(TAG, "camera configured.");
                            BaseCamera2Activity.this.session = session;

                            try {
                                SharedPreferences prefs = getSharedPreferences(
                                        "camera_settings", Context.MODE_PRIVATE);
                                float zoomRatio = Float.parseFloat(prefs.getString(ZOOM, "0"));
                                int whiteBalance = Integer.parseInt(prefs.getString("white_balance", "5700"));
                                int aeCompensation = Integer.parseInt(prefs.getString("ae_compensation", "0"));

                                CaptureRequest.Builder previewRequest = prepareCameraSettings(CameraDevice.TEMPLATE_PREVIEW, aeCompensation, whiteBalance, zoomRatio);

                                previewRequest.addTarget(holder.getSurface());

                                session.setRepeatingRequest(previewRequest.build(), null, null);

                                capureButton.setOnClickListener(v -> {
                                    try {
                                        capture(session);
                                    } catch (CameraAccessException cameraAccessException) {
                                        cameraAccessException.printStackTrace();
                                    }
                                });

                                onCameraConfigured(cameraProperties, camera);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @RequiresApi(api = Build.VERSION_CODES.R)
                        private void capture(@NonNull CameraCaptureSession session) throws CameraAccessException {
                            try {
                                SharedPreferences prefs2 = PreferenceManager.getDefaultSharedPreferences(BaseCamera2Activity.this);
                                float currentZoomRatio = Float.parseFloat(prefs2.getString(ZOOM, "0"));
                                int currentWhiteBalance = Integer.parseInt(prefs2.getString(WHITE_BALANCE, "5700"));
                                int currentAeCompensation = Integer.parseInt(prefs2.getString(AE_COMPENSATION, "0"));

                                CaptureRequest.Builder captureRequest = prepareCameraSettings(CameraDevice.TEMPLATE_STILL_CAPTURE, currentAeCompensation, currentWhiteBalance, currentZoomRatio);
                                captureRequest.set(CaptureRequest.JPEG_ORIENTATION, currentCameraCharacteristics.sensorOrientation);


                                captureRequest.addTarget(imageReader.getSurface());

                                session.captureSingleRequest(captureRequest.build(), getMainExecutor(), new CameraCaptureSession.CaptureCallback() {
                                    @Override
                                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                        super.onCaptureCompleted(session, request, result);
                                        imageReader.setOnImageAvailableListener(reader -> {
                                            DateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddhhmmss");
                                            Uri fileUri = getPhotoFileUri("capture_" + simpleDateFormat.format(new Date()) + ".jpg");
                                            try (OutputStream outputStream = getContentResolver().openOutputStream(fileUri);
                                                 Image image = imageReader.acquireLatestImage();) {
                                                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                                byte[] bytes = new byte[buffer.remaining()];
                                                buffer.get(bytes);
                                                outputStream.write(bytes);

                                                Log.i(TAG, "captured.");
                                                Intent intent = new Intent();
                                                intent.setData(fileUri);
                                                setResult(Activity.RESULT_OK, intent);
                                                finish();
                                            } catch (FileNotFoundException e) {
                                                e.printStackTrace();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }

                                        }, null);

                                    }
                                });


                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                        }
                    }, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
            }
        }, mBackgroundHandler);
    }

    protected abstract void onCameraConfigured(CameraProperties cameraProperties, CameraDevice camera) throws CameraAccessException;

    @RequiresApi(api = Build.VERSION_CODES.R)
    protected CaptureRequest.Builder prepareCameraSettings(int templateType, int aeCompensation, int colorTemp, float zoomRatio) throws CameraAccessException {
        CaptureRequest.Builder captureRequest = currentCamera.createCaptureRequest(templateType);
        captureRequest.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
        // disable OIS
        captureRequest.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        // adjust color correction using seekbar's params
        captureRequest.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
        captureRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeCompensation);
        captureRequest.set(CaptureRequest.COLOR_CORRECTION_GAINS, ImageUtils.colorTemperature(colorTemp));
        captureRequest.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);
        captureRequest.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
        Log.i(TAG, "prepare camera zoom set to " + zoomRatio);
        return captureRequest;
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

    @RequiresApi(api = Build.VERSION_CODES.R)
    protected void updateCameraState() throws CameraAccessException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int colorTemp = Integer.parseInt(prefs.getString(WHITE_BALANCE, "5700"));
        int aeCompenstaion = Integer.parseInt(prefs.getString(AE_COMPENSATION, "0"));
        float zoomRatio = Float.parseFloat(prefs.getString(ZOOM, "0"));

        CaptureRequest.Builder captureRequest = prepareCameraSettings(CameraDevice.TEMPLATE_PREVIEW, aeCompenstaion, colorTemp, zoomRatio);
        captureRequest.addTarget(holder.getSurface());
        session.setRepeatingRequest(captureRequest.build(), null, null);
    }
}

