package org.fao.mobile.woodidentifier;

import static android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.AE_COMPENSATION;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CROP_X;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CROP_Y;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CUSTOM_AWB;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CUSTOM_AWB_VALUES;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.EXPOSURE_TIME;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.FRAME_DURATION_TIME;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.SENSOR_SENSITIVITY;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.WHITE_BALANCE;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.ZOOM;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.isCustomAWB;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.isCustomExposure;

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
import android.hardware.camera2.CaptureResult;
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
    public static final String CUSTOM_AWB_DEFAULT_VALUE = "2.0,1.0,2.0";

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
    private float[] currentAWBDelta = new float[]{255.0f, 255.0f, 255.0f};
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_PICTURE_TAKEN = 3;
    private static final int STATE_WAITING_LOCK_FOCUS_ONLY = 4;

    int mCurrentState = STATE_PREVIEW;

    CameraCaptureSession.StateCallback cameraStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "camera configured.");
            BaseCamera2Activity.this.session = session;
            startPreview(session);
        }

        private void startPreview(@NonNull CameraCaptureSession session) {
            try {
                CaptureRequest.Builder previewRequest = prepareCameraSettings(TEMPLATE_PREVIEW);
                previewRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                previewRequest.addTarget(holder.getSurface());
                previewRequest.addTarget(autoAWBReader.getSurface());
                autoAWBReader.setOnImageAvailableListener(reader -> {
                    Image image = reader.acquireNextImage();

                    if (image == null) {
                        return;
                    }

                    currentAWBDelta = ImageUtils.computeAWBDelta(image);
                    try {
                        image.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, mAnalysisHandler);

                session.setRepeatingRequest(previewRequest.build(), null, null);
                for (View button : capureButton) {
                    button.setOnClickListener(v -> {
                        try {
                            lockFocusAndPicture();
                        } catch (CameraAccessException cameraAccessException) {
                            cameraAccessException.printStackTrace();
                        }
                    });
                }



                onCameraConfigured(currentCameraCharacteristics, currentCamera);
                runOnUiThread(() -> {
                    tapToFocusLabel.setVisibility(View.VISIBLE);
                    for (View button : capureButton) {
                        button.setEnabled(true);
                    }
                    cameraFrame.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                Log.i(TAG, "focus start");
                                lockFocus();
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                });

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) throws CameraAccessException {
            switch (mCurrentState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        capture(session);
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        mCurrentState = STATE_PICTURE_TAKEN;
                        capture(session);
                    }
                }
                break;
                case STATE_WAITING_LOCK_FOCUS_ONLY: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        unlockFocus();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState) {
                        Log.i(TAG, "focus locked");
                        unlockFocus();
                    } else if (CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        Log.i(TAG, "unable to focus");
                        unlockFocus();
                    }
                    tapToFocusLabel.setVisibility(View.VISIBLE);
                }
                break;
            }
        }


        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            try {
                process(partialResult);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            try {
                process(result);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

    };
    private View tapToFocusLabel;

    private void lockFocusAndPicture() throws CameraAccessException {
        session.stopRepeating();
        CaptureRequest.Builder captureRequest = currentCamera.createCaptureRequest(TEMPLATE_PREVIEW);
        captureRequest.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
        // disable OIS
        captureRequest.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        captureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        captureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START);
        // Tell #mCaptureCallback to wait for the lock.
        mCurrentState = STATE_WAITING_LOCK;
        tapToFocusLabel.setVisibility(View.GONE);
        captureRequest.addTarget(holder.getSurface());
        session.capture(captureRequest.build(), mCaptureCallback, null);
    }

    private void lockFocus() throws CameraAccessException {
        CaptureRequest.Builder captureRequest = prepareCameraSettings(TEMPLATE_PREVIEW);
        captureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START);
        // Tell #mCaptureCallback to wait for the lock.
        mCurrentState = STATE_WAITING_LOCK_FOCUS_ONLY;
        captureRequest.addTarget(holder.getSurface());
        tapToFocusLabel.setVisibility(View.GONE);
        session.setRepeatingRequest(captureRequest.build(), mCaptureCallback, null);
    }

    private CaptureRequest.Builder prepareCameraSettings(int template) throws CameraAccessException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(BaseCamera2Activity.this);
        float zoomRatio = Float.parseFloat(prefs.getString(ZOOM, "0"));
        int whiteBalance = Integer.parseInt(prefs.getString(WHITE_BALANCE, "5700"));
        int aeCompensation = Integer.parseInt(prefs.getString(AE_COMPENSATION, "0"));
        float[] customAwb = StringUtils.splitToFloatList(prefs.getString(CUSTOM_AWB_VALUES, "255,255,255"));
        int sensorSensitivity = Integer.parseInt(prefs.getString(SENSOR_SENSITIVITY, "200"));
        long frameDuration = Long.parseLong(prefs.getString(FRAME_DURATION_TIME, "1000000"));
        long exposureTime = Long.parseLong(prefs.getString(EXPOSURE_TIME, "40494"));
        Rect sensorCrop = getSensorRect(prefs, zoomRatio, currentCameraCharacteristics);
        return prepareCameraSettings(template, aeCompensation, whiteBalance, zoomRatio, sensorCrop, customAwb, exposureTime, sensorSensitivity, frameDuration);
    }

    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            CaptureRequest.Builder captureRequest = prepareCameraSettings(TEMPLATE_PREVIEW);
            captureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            captureRequest.addTarget(holder.getSurface());
            mCurrentState = STATE_PREVIEW;
            session.capture(captureRequest.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void capture(@NonNull CameraCaptureSession session) throws CameraAccessException {
        try {
            CaptureRequest.Builder captureRequest = prepareCameraSettings(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequest.set(CaptureRequest.JPEG_ORIENTATION, currentCameraCharacteristics.sensorOrientation);
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

            captureRequest.addTarget(imageReader.getSurface());
            captureRequest.addTarget(autoAWBReader.getSurface());
            autoAWBReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireNextImage();
                float[] avgDelta = ImageUtils.computeAWBDelta(image);
                Log.i(TAG, "awbDelta " + (avgDelta[0] / 255.0f) * 100f + "% , " + (avgDelta[1] / 255.0f) * 100f + "%, " + (avgDelta[2] / 255.0f) * 100f + "%");
                image.close();
            }, mAnalysisHandler);

            session.stopRepeating();
            session.abortCaptures();
            session.capture(captureRequest.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        this.capureButton = getCaptureButton();
        View cancelButton = getCancelButton();
        SurfaceView viewFinder = getCameraPreviewTextureView();
        this.cameraFrame = findViewById(R.id.cameraFrame);
        this.tapToFocusLabel = findViewById(R.id.tap_to_focus);
        tapToFocusLabel.setVisibility(View.GONE);
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
                Range<Long> exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                Range<Integer> sensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                Long maxFrameDuration = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
                Size size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                Range<Float> zoomRatioRange = null;

                Range<Integer> aeCompensationRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (cameraLensFacing == CameraMetadata.LENS_FACING_BACK) {
                    Log.i(TAG, "camera " + cameraId + " is backfacing");
                    Log.i(TAG, "sensor orientation is " + sensorOrientation);
                    Log.i(TAG, "camera resolution " + size.getWidth() + "," + size.getHeight());
                    if (exposureTimeRange != null) {
                        Log.i(TAG, "exposure range " + exposureTimeRange.getLower() + " to " + exposureTimeRange.getUpper());
                    }
                    if (sensitivityRange != null) {
                        Log.i(TAG, "sensitivity range " + sensitivityRange.getLower() + " to " + sensitivityRange.getUpper());
                    }
                    Log.i(TAG, "max frame duration " + maxFrameDuration);
                    for (float focalLength : focalLengths) {
                        Log.i(TAG, "focal length: " + focalLength);
                    }
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
                    Log.i(TAG, "selected resolution " + maxSize);
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
        for (View button : capureButton) {
            button.setEnabled(false);
        }

        cameraManager.openCamera(cameraProperties.cameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                BaseCamera2Activity.this.currentCamera = camera;

                ArrayList<Surface> outputs = new ArrayList<>();

                int autoCropSize = Math.min(cameraProperties.size.getWidth(), cameraProperties.size.getHeight());
                autoAWBReader = ImageReader.newInstance(autoCropSize, autoCropSize, ImageFormat.YUV_420_888, 1);
                imageReader = ImageReader.newInstance(autoCropSize, autoCropSize, ImageFormat.JPEG, 1);

                Surface surface = holder.getSurface();
                outputs.add(surface);
                outputs.add(imageReader.getSurface());
                outputs.add(autoAWBReader.getSurface());

                try {
                    camera.createCaptureSession(outputs, cameraStateCallback, mBackgroundHandler);
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
            autoCropSize = (int) (((autoCropSize - 512.0f) * (1f - ((zoomRatio - 1f) / 9.0f))) + 512.0f);
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

    protected CaptureRequest.Builder prepareCameraSettings(int templateType, int aeCompensation, int colorTemp, float zoomRatio, Rect cropRegion, float[] awbDelta, long sensorExposureTime, int sensitivity, long frameDuration) throws CameraAccessException {
        CaptureRequest.Builder captureRequest = currentCamera.createCaptureRequest(templateType);
        captureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        captureRequest.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
        // disable OIS
        captureRequest.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);

        //Set all to max quality

        captureRequest.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY);
        captureRequest.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_HIGH_QUALITY);
        captureRequest.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_HIGH_QUALITY);
        captureRequest.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
        captureRequest.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
        captureRequest.set(CaptureRequest.HOT_PIXEL_MODE, CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY);
        captureRequest.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY);
        // adjust color correction using seekbar's params

        captureRequest.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
        if (isCustomExposure(this)) {
            captureRequest.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            captureRequest.set(CaptureRequest.SENSOR_EXPOSURE_TIME, sensorExposureTime);
            captureRequest.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity);
            captureRequest.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration);
        } else {
            Log.i(TAG, "AE Compensation = " + aeCompensation);
            captureRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeCompensation);

        }


        if (isCustomAWB(this)) {
            RggbChannelVector channelVector = new RggbChannelVector(awbDelta[0], awbDelta[1], awbDelta[1], awbDelta[2]);
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
        CaptureRequest.Builder captureRequest = prepareCameraSettings(TEMPLATE_PREVIEW);
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

        int sensorSensitivity = Integer.parseInt(prefs2.getString(SENSOR_SENSITIVITY, "200"));
        long frameDuration = Long.parseLong(prefs2.getString(FRAME_DURATION_TIME, "1000000"));
        long exposureTime = Long.parseLong(prefs2.getString(EXPOSURE_TIME, "40494"));

        CaptureRequest.Builder captureRequest = prepareCameraSettings(CameraDevice.TEMPLATE_STILL_CAPTURE, currentAeCompensation, currentWhiteBalance, currentZoomRatio, sensorCrop, new float[]{255f, 255f, 255f}, exposureTime, sensorSensitivity, frameDuration);
        captureRequest.set(CaptureRequest.JPEG_ORIENTATION, currentCameraCharacteristics.sensorOrientation);
        captureRequest.addTarget(autoAWBReader.getSurface());

        session.capture(captureRequest.build(), new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                autoAWBReader.setOnImageAvailableListener(reader -> {
                    Image image = reader.acquireNextImage();
                    float[] avgDelta = ImageUtils.computeAWBDelta(image);
                    Log.i(TAG, "awbDelta " + (avgDelta[0] / 255.0f) * 100f + "% , " + (avgDelta[1] / 255.0f) * 100f + "%, " + (avgDelta[2] / 255.0f) * 100f + "%");
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

