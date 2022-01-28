package org.fao.mobile.woodidentifier;

import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.AE_COMPENSATION;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CROP_X;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CROP_Y;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CUSTOM_AWB;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CUSTOM_AWB_VALUES;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.WHITE_BALANCE;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.ZOOM;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.isCustomAWB;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public abstract class BaseCamera2Activity extends AppCompatActivity {
    private static final String PHONE_DATABASE = "phone_database.json";
    private final String TAG = BaseCamera2Activity.class.getCanonicalName();
    protected CameraProperties currentCameraCharacteristics;
    protected SurfaceHolder holder;
    private Handler mBackgroundHandler;
    private Handler mAnalysisHandler;

    protected CameraManager cameraManager;
    protected CameraDevice currentCamera;
    protected CameraCaptureSession session;
    private View[] capureButton;
    private View cameraFrame;
    private ImageReader imageReader;
    private ImageReader autoAWBReader;

    @SuppressLint("MissingPermission")
    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        this.capureButton = getCaptureButton();
        View cancelButton = getCancelButton();
        SurfaceView viewFinder = getCameraPreviewTextureView();
        this.cameraFrame = findViewById(R.id.cameraFrame);
        this.cameraManager = (CameraManager) getSystemService(Service.CAMERA_SERVICE);
        startBackgroundThread();

        viewFinder.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                BaseCamera2Activity.this.holder = holder;
                try {
                    setupCamera(holder);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
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

    protected abstract View[] getCaptureButton();

    protected abstract View getCancelButton();

    abstract SurfaceView getCameraPreviewTextureView();

    protected void startBackgroundThread() {
        HandlerThread mBackgroundThread = new HandlerThread("ModuleActivity");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        HandlerThread mAnalysis = new HandlerThread("Analysis");
        mAnalysis.start();
        mAnalysisHandler = new Handler(mAnalysis.getLooper());
    }

    private void setupCamera(SurfaceHolder holder) throws CameraAccessException {
        ArrayList<CameraProperties> backFacingCameras = enumerateBackCameras();
        int currentCamera = SharedPrefsUtil.getCurrentCamera(this);
        CameraProperties camera = backFacingCameras.get(currentCamera);

        // fix to maintain correct aspect ratio in screen

        runOnUiThread(() -> {
            Size size = camera.getSize();

            int autoCrop = Math.min(cameraFrame.getWidth(), cameraFrame.getHeight());
            onCameraFrameSet(cameraFrame, autoCrop);


            try {
                setupCamera(holder, camera);
                onSetupCameraComplete(backFacingCameras, camera);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        });
    }

    protected abstract void onCameraFrameSet(View cameraFrame, int autoCrop);

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

        @NonNull
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
                Range<Float> zoomRatioRange = null;

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
                            if (maxResolution < (long) s.getWidth() * s.getHeight()) {
                                maxResolution = (long) s.getWidth() * s.getHeight();
                                maxSize = s;
                            }
                        }
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !isForceDigitalZoom(PreferenceManager.getDefaultSharedPreferences(BaseCamera2Activity.this))) {
                        zoomRatioRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                        Log.i(TAG, "Using hardware zoom ratio");
                    } else {
                        zoomRatioRange = new Range<Float>(1.0f, 10.0f);
                        Log.i(TAG, "Using sensor crop");
                    }

                    List<String> outputFormatStr = Arrays.stream(outputFormats).mapToObj(Integer::toString).collect(Collectors.toList());
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
    protected void setupCamera(SurfaceHolder holder, CameraProperties cameraProperties) throws CameraAccessException {
        this.currentCameraCharacteristics = cameraProperties;
        if (currentCamera != null) {
            currentCamera.close();
        }
        for(View button: capureButton) {
            button.setEnabled(false);
        }

        cameraManager.openCamera(cameraProperties.cameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                BaseCamera2Activity.this.currentCamera = camera;


                ArrayList<Surface> outputs = new ArrayList<>();
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(BaseCamera2Activity.this);
                int autoCropSize = Math.min(cameraProperties.size.getWidth(), cameraProperties.size.getHeight());
                autoAWBReader = ImageReader.newInstance(autoCropSize, autoCropSize, ImageFormat.YUV_420_888, 1);
                imageReader = ImageReader.newInstance(autoCropSize, autoCropSize, ImageFormat.JPEG, 1);

                Surface surface = holder.getSurface();
                outputs.add(surface);
                outputs.add(imageReader.getSurface());
                outputs.add(autoAWBReader.getSurface());

                try {
                    camera.createCaptureSession(outputs, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            Log.i(TAG, "camera configured.");
                            BaseCamera2Activity.this.session = session;

                            try {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(BaseCamera2Activity.this);
                                float zoomRatio = Float.parseFloat(prefs.getString(ZOOM, "0"));
                                int whiteBalance = Integer.parseInt(prefs.getString(WHITE_BALANCE, "5700"));
                                int aeCompensation = Integer.parseInt(prefs.getString(AE_COMPENSATION, "0"));
                                float[] customAwb = StringUtils.splitToFloatList(prefs.getString(CUSTOM_AWB_VALUES, "255,255,255"));

                                Rect sensorCrop = getSensorRect(prefs, zoomRatio, cameraProperties);
                                CaptureRequest.Builder previewRequest = prepareCameraSettings(CameraDevice.TEMPLATE_PREVIEW, aeCompensation, whiteBalance, zoomRatio, sensorCrop, customAwb);

                                previewRequest.addTarget(holder.getSurface());

                                session.setRepeatingRequest(previewRequest.build(), null, null);
                                for(View button: capureButton) {
                                    button.setOnClickListener(v -> {
                                        try {
                                            capture(session);
                                        } catch (CameraAccessException cameraAccessException) {
                                            cameraAccessException.printStackTrace();
                                        }
                                    });
                                }
                                onCameraConfigured(cameraProperties, camera);
                                runOnUiThread(() -> {
                                    for(View button: capureButton) {
                                        button.setEnabled(true);
                                    }
                                });

                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        private void capture(@NonNull CameraCaptureSession session) throws CameraAccessException {
                            try {
                                SharedPreferences prefs2 = PreferenceManager.getDefaultSharedPreferences(BaseCamera2Activity.this);
                                float currentZoomRatio = Float.parseFloat(prefs2.getString(ZOOM, "0"));
                                int currentWhiteBalance = Integer.parseInt(prefs2.getString(WHITE_BALANCE, "5700"));
                                float[] customAwb = StringUtils.splitToFloatList(prefs2.getString(CUSTOM_AWB_VALUES, "255,255,255"));
                                int currentAeCompensation = Integer.parseInt(prefs2.getString(AE_COMPENSATION, "0"));
                                Rect sensorCrop = getSensorRect(prefs2, currentZoomRatio, cameraProperties);
                                CaptureRequest.Builder captureRequest = prepareCameraSettings(CameraDevice.TEMPLATE_STILL_CAPTURE, currentAeCompensation, currentWhiteBalance, currentZoomRatio, sensorCrop, customAwb);
                                captureRequest.set(CaptureRequest.JPEG_ORIENTATION, currentCameraCharacteristics.sensorOrientation);
                                captureRequest.addTarget(imageReader.getSurface());
//                                captureRequest.addTarget(autoAWBReader.getSurface());
//                                autoAWBReader.setOnImageAvailableListener(reader -> {
//                                    Image image = reader.acquireNextImage();
//                                    float[] avgDelta = ImageUtils.computeAWBDelta(image);
//                                    Log.i(TAG, "awbDelta " + avgDelta[0] + " , " + avgDelta[1] + ", " + avgDelta[2]);
//                                    float min = Math.min(avgDelta[0], Math.min(avgDelta[1], avgDelta[2]));
//                                    float[] awbval = new float[]{255f - (avgDelta[0] - min), 255f - (avgDelta[1] - min), 255f - (avgDelta[2] - min)};
//                                    long colorTemp = ImageUtils.colorTempFromRgb(awbval[0], awbval[1], awbval[2]);
//                                    Log.i(TAG, "awbDelta " + awbval[0] + " , " + awbval[1] + ", " + awbval[2]);
//                                    Log.i(TAG, "estimated color temp " + colorTemp + "K");
//                                    try {
//                                        image.close();
//                                        Log.i(TAG, "Setting AWB offsets");
//                                        updateCameraState();
//                                    } catch (Exception e) {
//                                        e.printStackTrace();
//                                    }
//                                }, mAnalysisHandler);
                                session.capture(captureRequest.build(), new CameraCaptureSession.CaptureCallback() {
                                    @Override
                                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                        super.onCaptureCompleted(session, request, result);
                                        imageReader.setOnImageAvailableListener(reader -> {
                                            DateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());

                                            Uri fileUri = ImageUtils.getPhotoFileUri(BaseCamera2Activity.this, "capture_" + simpleDateFormat.format(new Date()) + ".jpg");
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
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }

                                        }, null);

                                    }
                                }, mBackgroundHandler);
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

    @NonNull
    private Rect getSensorRect(SharedPreferences prefs, float zoomRatio, CameraProperties cameraProperties) {
        Size hardwareSize = cameraProperties.getSize();
        int autoCropSize = Math.min(hardwareSize.getWidth(), hardwareSize.getHeight());
        int wCrop;
        int hCrop;
        if (isForceDigitalZoom(prefs)) {
            autoCropSize = (int) (((autoCropSize - 512.0f) * ( 1f- ((zoomRatio - 1f)/ 9.0f))) + 512.0f);
            wCrop = autoCropSize;
            hCrop = autoCropSize;
        } else {
            wCrop = Integer.parseInt(prefs.getString(CROP_X, Integer.toString(autoCropSize)));
            hCrop = Integer.parseInt(prefs.getString(CROP_Y, Integer.toString(autoCropSize)));
        }

        int left = hardwareSize.getWidth() / 2 - wCrop / 2;
        int top = hardwareSize.getHeight() / 2 - hCrop / 2;
        int right = left + wCrop;
        int bottom = top + hCrop;
        return new Rect(left, top, right, bottom);
    }

    private boolean isForceDigitalZoom(SharedPreferences prefs) {
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) || prefs.getBoolean("force_digital_zoom", false);
    }

    protected abstract void onCameraConfigured(CameraProperties cameraProperties, CameraDevice camera) throws CameraAccessException;

    protected CaptureRequest.Builder prepareCameraSettings(int templateType, int aeCompensation, int colorTemp, float zoomRatio, Rect cropRegion, float[] awbDelta) throws CameraAccessException {
        CaptureRequest.Builder captureRequest = currentCamera.createCaptureRequest(templateType);
        captureRequest.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
        // disable OIS
        captureRequest.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        // adjust color correction using seekbar's params
        captureRequest.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
        captureRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeCompensation);

        if (isCustomAWB(this)) {
            RggbChannelVector channelVector = new RggbChannelVector((awbDelta[0] / 255f) * 2f, (awbDelta[1] / 255f), (awbDelta[1] / 255f), (awbDelta[2] / 255f) * 2f);
            Log.i(TAG, awbDelta[0] + "," + awbDelta[1] + "," + awbDelta[2]);
            captureRequest.set(CaptureRequest.COLOR_CORRECTION_GAINS, channelVector);
        } else {
            RggbChannelVector gains = ImageUtils.colorTemperature(colorTemp);
            captureRequest.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            captureRequest.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);
        }

        captureRequest.set(CaptureRequest.SCALER_CROP_REGION, cropRegion);
        captureRequest.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
        Log.i(TAG, "prepare camera zoom set to " + zoomRatio);
        Log.i(TAG, "hardware crop size set to (" + cropRegion.top + "," + cropRegion.left + "," + cropRegion.right + "," + cropRegion.bottom + ")");
        return captureRequest;
    }


    protected void updateCameraState() throws CameraAccessException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int colorTemp = Integer.parseInt(prefs.getString(WHITE_BALANCE, "5700"));
        int aeCompenstaion = Integer.parseInt(prefs.getString(AE_COMPENSATION, "0"));
        float[] customAwb = StringUtils.splitToFloatList(prefs.getString(CUSTOM_AWB_VALUES, "255,255,255"));
        float zoomRatio = Float.parseFloat(prefs.getString(ZOOM, "1.0"));
        Rect sensorCrop = getSensorRect(prefs, zoomRatio, currentCameraCharacteristics);
        CaptureRequest.Builder captureRequest = prepareCameraSettings(CameraDevice.TEMPLATE_PREVIEW, aeCompenstaion, colorTemp, zoomRatio, sensorCrop, customAwb);
        captureRequest.addTarget(holder.getSurface());
        session.setRepeatingRequest(captureRequest.build(), null, null);
    }


    protected void acquireAWBLock() throws CameraAccessException {
        SharedPreferences prefs2 = PreferenceManager.getDefaultSharedPreferences(BaseCamera2Activity.this);
        float currentZoomRatio = Float.parseFloat(prefs2.getString(ZOOM, "0"));
        int currentWhiteBalance = Integer.parseInt(prefs2.getString(WHITE_BALANCE, "5700"));

        int currentAeCompensation = Integer.parseInt(prefs2.getString(AE_COMPENSATION, "0"));
        Rect sensorCrop = getSensorRect(prefs2, currentZoomRatio, currentCameraCharacteristics);
        prefs2.edit()
                .putBoolean(CUSTOM_AWB, true)
                .commit();
        CaptureRequest.Builder captureRequest = prepareCameraSettings(CameraDevice.TEMPLATE_STILL_CAPTURE, currentAeCompensation, currentWhiteBalance, currentZoomRatio, sensorCrop, new float[]{255f, 255f, 255f});
        captureRequest.set(CaptureRequest.JPEG_ORIENTATION, currentCameraCharacteristics.sensorOrientation);
        captureRequest.addTarget(autoAWBReader.getSurface());

        session.capture(captureRequest.build(), new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                autoAWBReader.setOnImageAvailableListener(reader -> {
                    Image image = reader.acquireNextImage();
                    float[] avgDelta = ImageUtils.computeAWBDelta(image);
                    Log.i(TAG, "awbDelta " + avgDelta[0] + " , " + avgDelta[1] + ", " + avgDelta[2]);
                    float min = Math.min(avgDelta[0], Math.min(avgDelta[1], avgDelta[2]));
                    float[] awbDelta = new float[]{255f - (avgDelta[0] - min), 255f - (avgDelta[1] - min), 255f - (avgDelta[2] - min)};
                    long colorTemp = ImageUtils.colorTempFromRgb(awbDelta[0], awbDelta[1], awbDelta[2]);
                    Log.i(TAG, "awbDelta " + awbDelta[0] + " , " + awbDelta[1] + ", " + awbDelta[2]);
                    Log.i(TAG, "estimated color temp " + colorTemp + "K");
                    prefs2.edit()
                            .putString(WHITE_BALANCE, Long.toString(colorTemp))
                            .putString(CUSTOM_AWB_VALUES, awbDelta[0] + "," + awbDelta[1] + "," + awbDelta[2])
                            .putBoolean(CUSTOM_AWB, true)
                            .commit();
                    try {
                        image.close();
                        Log.i(TAG, "Setting AWB offsets");
                        updateCameraState();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, mAnalysisHandler);
            }
        }, mBackgroundHandler);
    }

}

