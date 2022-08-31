package org.fao.mobile.woodidentifier;

import static android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW;
import static android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.AE_COMPENSATION;
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
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
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
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.fao.mobile.woodidentifier.utils.ImageUtils;
import org.fao.mobile.woodidentifier.utils.SharedPrefsUtil;
import org.fao.mobile.woodidentifier.utils.StringUtils;
import org.fao.mobile.woodidentifier.views.AutoFitTextureView;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public abstract class BaseCamera2Activity extends AppCompatActivity {
    private static final String PHONE_DATABASE = "phone_database.json";
    public static final String CUSTOM_AWB_DEFAULT_VALUE = "2.0,1.0,2.0";
    private static final String TAG = BaseCamera2Activity.class.getCanonicalName();
    protected CameraProperties currentCameraCharacteristics;

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

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    int mCurrentState = STATE_PREVIEW;

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            try {
                setupCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

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

                Surface surface = getSurface();

                previewRequest.addTarget(surface);
                session.setRepeatingRequest(previewRequest.build(), null, null);
                for (View button : capureButton) {
                    button.setOnClickListener(v -> {
                        try {
                            capture(session);
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
                    cameraFrame.setOnClickListener((v) -> {
                        try {
                            Log.i(TAG, "focus start");
                            lockFocus();
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
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
    private Size mPreviewSize;
    private AutoFitTextureView mTextureView;

    private void lockFocus() throws CameraAccessException {
        CaptureRequest.Builder captureRequest = prepareCameraSettings(TEMPLATE_PREVIEW);
        captureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START);
        // Tell #mCaptureCallback to wait for the lock.
        mCurrentState = STATE_WAITING_LOCK_FOCUS_ONLY;
        Surface surface = new Surface(mTextureView.getSurfaceTexture());
        captureRequest.addTarget(surface);
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
        Rect sensorCrop = getSensorRect(prefs, zoomRatio, currentCameraCharacteristics.activeArraySize);
        return prepareCameraSettings(template, aeCompensation, whiteBalance, zoomRatio, sensorCrop, customAwb, exposureTime, sensorSensitivity, frameDuration);
    }

    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            CaptureRequest.Builder captureRequest = prepareCameraSettings(TEMPLATE_PREVIEW);
            captureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            Surface surface = new Surface(mTextureView.getSurfaceTexture());
            captureRequest.addTarget(surface);
            mCurrentState = STATE_PREVIEW;
            session.capture(captureRequest.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void capture(@NonNull CameraCaptureSession session) throws CameraAccessException {
        try {
            CaptureRequest.Builder captureRequest = prepareCameraSettings(TEMPLATE_STILL_CAPTURE);
            captureRequest.set(CaptureRequest.JPEG_ORIENTATION, currentCameraCharacteristics.sensorOrientation);
//            imageReader.setOnImageAvailableListener(reader -> {
//                DateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
//
//                Uri fileUri = ImageUtils.getPhotoFileUri(BaseCamera2Activity.this, "capture_" + simpleDateFormat.format(new Date()) + ".jpg");
//                try (OutputStream outputStream = getContentResolver().openOutputStream(fileUri);
//                     Image image = imageReader.acquireLatestImage();) {
//                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
//                    byte[] bytes = new byte[buffer.remaining()];
//                    buffer.get(bytes);
//                    outputStream.write(bytes);
//                    Log.i(TAG, "captured.");
//                    Intent intent = new Intent();
//                    intent.setData(fileUri);
//                    setResult(Activity.RESULT_OK, intent);
//                    finish();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//
//            }, null);
            imageReader.setOnImageAvailableListener((reader) -> {
                // Get the YUV data
                final Image image = reader.acquireLatestImage();
                final ByteBuffer yuvBytes = this.imageToByteBuffer(image);
                // Convert YUV to RGB
                final RenderScript rs = RenderScript.create(this);

                final Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                final Allocation allocationRgb = Allocation.createFromBitmap(rs, bitmap);

                final Allocation allocationYuv = Allocation.createSized(rs, Element.U8(rs), yuvBytes.array().length);
                allocationYuv.copyFrom(yuvBytes.array());

                ScriptIntrinsicYuvToRGB scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
                scriptYuvToRgb.setInput(allocationYuv);
                scriptYuvToRgb.forEach(allocationRgb);

                allocationRgb.copyTo(bitmap);

                Matrix matrix = new Matrix();
                RectF srcRect = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
                matrix.postRotate(90, srcRect.centerX(), srcRect.centerY());
//                RectF DestRect = new RectF(0, 0,currentCameraCharacteristics.activeArraySize.getHeight(), currentCameraCharacteristics.activeArraySize.getWidth());
                int centerCrop = Math.min(bitmap.getHeight(), bitmap.getWidth());
                int srcX = bitmap.getWidth() / 2 - centerCrop / 2;
                int srcY = bitmap.getHeight() / 2 - centerCrop / 2;

                Bitmap rotated  = Bitmap.createBitmap(bitmap, srcX, srcY, centerCrop, centerCrop, matrix, true);
                bitmap.recycle();
                DateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());

                Uri fileUri = ImageUtils.getPhotoFileUri(BaseCamera2Activity.this, "capture_" + simpleDateFormat.format(new Date()) + ".jpg");
                // Release
                try (OutputStream out = getContentResolver().openOutputStream(fileUri)){
                    rotated.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    out.flush();
                    Log.i(TAG, "captured.");
                    Intent intent = new Intent();
                    intent.setData(fileUri);
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    rotated.recycle();
                    allocationYuv.destroy();
                    allocationRgb.destroy();
                    rs.destroy();
                    image.close();
                }
            }, null);
            captureRequest.addTarget(imageReader.getSurface());
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

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            try {
                setupCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {
    }

    private void closeCamera() {

    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        this.capureButton = getCaptureButton();
        View cancelButton = getCancelButton();
        mTextureView = getCameraPreviewTextureView();
        this.cameraFrame = findViewById(R.id.cameraFrame);
        this.tapToFocusLabel = findViewById(R.id.tap_to_focus);
        tapToFocusLabel.setVisibility(View.GONE);
        this.cameraManager = (CameraManager) getSystemService(Service.CAMERA_SERVICE);

        cancelButton.setOnClickListener((view) -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
    }

    protected abstract View[] getCaptureButton();

    protected abstract View getCancelButton();

    abstract AutoFitTextureView getCameraPreviewTextureView();

    protected void startBackgroundThread() {
        HandlerThread mBackgroundThread = new HandlerThread("ModuleActivity");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        HandlerThread mAnalysis = new HandlerThread("Analysis");
        mAnalysis.start();
        mAnalysisHandler = new Handler(mAnalysis.getLooper());
    }

    private void setupCamera() throws CameraAccessException {
        ArrayList<CameraProperties> backFacingCameras = enumerateBackCameras();
        int currentCamera = SharedPrefsUtil.getCurrentCamera(this);
        CameraProperties camera = backFacingCameras.get(currentCamera);

        // fix to maintain correct aspect ratio in screen
        runOnUiThread(() -> {
            int autoCrop = Math.min(cameraFrame.getWidth(), cameraFrame.getHeight());
            onCameraFrameSet(cameraFrame, autoCrop);
            try {
                setupCamera(camera);
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

        public Size getActiveArraySize() {
            return activeArraySize;
        }

        private final Size activeArraySize;

        public String getCameraId() {
            return cameraId;
        }

        public Size getSize() {
            return size;
        }

        private final Size size;

        public Size getPreviewSize() {
            return previewSize;
        }

        private final Size previewSize;

        public CameraProperties(String cameraId, Size size, Size previewSize, Size activeArraySize, Range<Float> zoomRatioRange, Range<Integer> aeCompensationRange, Integer sensorOrientation) {
            this.cameraId = cameraId;
            this.size = size;
            this.zoomRatioRange = zoomRatioRange;
            this.aeCompensationRange = aeCompensationRange;
            this.sensorOrientation = sensorOrientation;
            this.previewSize = previewSize;
            this.activeArraySize = activeArraySize;
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
                Rect activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                Size activeArraySize = new Size(activeArray.right - activeArray.left, activeArray.bottom - activeArray.top);
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
                    Size[] outputSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
                    Size[] previewSizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);

                    int[] outputFormats = streamConfigurationMap.getOutputFormats();

                    Size maxSize = Collections.max(
                            Arrays.asList(outputSizes),
                            new CompareSizesByArea());

                    if (outputSizes != null) {
                        for (Size s : outputSizes) {
                            Log.i(TAG, "imageReader output resolution " + s.getWidth() + "," + s.getHeight());
                        }
                    }

                    if (previewSizes != null) {
                        for (Size s : previewSizes) {
                            Log.i(TAG, "previewSizes output resolution " + s.getWidth() + "," + s.getHeight());
                        }
                    }

                    int autoCrop = Math.min(cameraFrame.getWidth(), cameraFrame.getHeight());

                    Size pSize = chooseOptimalSize(previewSizes,
                            autoCrop, autoCrop, MAX_PREVIEW_WIDTH,
                            MAX_PREVIEW_HEIGHT, maxSize);

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
                    Log.i(TAG, "selected capture resolution " + maxSize);
                    Log.i(TAG, "selected preview resolution " + pSize);
                    Log.i(TAG, "active array size " + activeArraySize);
                    RecalibrateCameraActivity.CameraProperties properties = new RecalibrateCameraActivity.CameraProperties(cameraId, maxSize, pSize, activeArraySize, zoomRatioRange, aeCompensationRange, sensorOrientation);
                    backFacingCameras.add(properties);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        return backFacingCameras;
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @SuppressLint("MissingPermission")
    protected void setupCamera(CameraProperties cameraProperties) throws CameraAccessException {
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
                mPreviewSize = cameraProperties.previewSize;
                int autoCropP = Math.min(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                runOnUiThread(() -> {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                });


//                int autoCropSize = Math.min(cameraProperties.size.getWidth(), cameraProperties.size.getHeight());
                autoAWBReader = ImageReader.newInstance(cameraProperties.size.getWidth(), cameraProperties.size.getHeight(), ImageFormat.YUV_420_888, 1);
                imageReader = ImageReader.newInstance(cameraProperties.size.getWidth(), cameraProperties.size.getHeight(), ImageFormat.YUV_420_888, 1);

                Surface surface = getSurface();

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


    private ByteBuffer imageToByteBuffer(final Image image) {
        final Rect crop = image.getCropRect();
        final int width = crop.width();
        final int height = crop.height();

        final Image.Plane[] planes = image.getPlanes();
        final byte[] rowData = new byte[planes[0].getRowStride()];
        final int bufferSize = width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
        final ByteBuffer output = ByteBuffer.allocateDirect(bufferSize);

        int channelOffset = 0;
        int outputStride = 0;

        for (int planeIndex = 0; planeIndex < 3; planeIndex++) {
            if (planeIndex == 0) {
                channelOffset = 0;
                outputStride = 1;
            } else if (planeIndex == 1) {
                channelOffset = width * height + 1;
                outputStride = 2;
            } else if (planeIndex == 2) {
                channelOffset = width * height;
                outputStride = 2;
            }

            final ByteBuffer buffer = planes[planeIndex].getBuffer();
            final int rowStride = planes[planeIndex].getRowStride();
            final int pixelStride = planes[planeIndex].getPixelStride();

            final int shift = (planeIndex == 0) ? 0 : 1;
            final int widthShifted = width >> shift;
            final int heightShifted = height >> shift;

            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));

            for (int row = 0; row < heightShifted; row++) {
                final int length;

                if (pixelStride == 1 && outputStride == 1) {
                    length = widthShifted;
                    buffer.get(output.array(), channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (widthShifted - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);

                    for (int col = 0; col < widthShifted; col++) {
                        output.array()[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }

                if (row < heightShifted - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }

        return output;
    }

    @NonNull
    private Surface getSurface() {
        // set holder preview size
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        assert texture != null;

        // We configure the size of default buffer to be the size of camera preview we want.
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        // This is the output Surface we need to start preview.
        return new Surface(texture);
    }

    @NonNull
    private Rect getSensorRect(SharedPreferences prefs, float zoomRatio, Size hardwareSize) {
        int autoCropSize = Math.min(hardwareSize.getWidth(), hardwareSize.getHeight());

        if (isForceDigitalZoom(prefs)) {
            autoCropSize = (int) (((autoCropSize - 512.0f) * (1f - ((zoomRatio - 1f) / 9.0f))) + 512.0f);
        }

        int wCrop = autoCropSize;
        int hCrop = autoCropSize;
        int left = (hardwareSize.getWidth() / 2) - (wCrop / 2);
        int top = (hardwareSize.getHeight() / 2) - (hCrop / 2);
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
            Log.i(TAG, "prepare camera zoom set to " + zoomRatio);
        }

//        captureRequest.set(CaptureRequest.SCALER_CROP_REGION, cropRegion);
        captureRequest.set(CaptureRequest.JPEG_QUALITY, (byte) 100);

        Log.i(TAG, "hardware crop size set to (" + cropRegion.left + "," + cropRegion.top + "," + cropRegion.right + "," + cropRegion.bottom + ")");
        return captureRequest;
    }


    protected void updateCameraState() throws CameraAccessException {
        CaptureRequest.Builder captureRequest = prepareCameraSettings(TEMPLATE_PREVIEW);
        captureRequest.addTarget(getSurface());
        session.setRepeatingRequest(captureRequest.build(), null, null);
    }

    protected void acquireAWBLock() throws CameraAccessException {
        SharedPreferences prefs2 = PreferenceManager.getDefaultSharedPreferences(BaseCamera2Activity.this);
        float currentZoomRatio = Float.parseFloat(prefs2.getString(ZOOM, "0"));
        int currentWhiteBalance = Integer.parseInt(prefs2.getString(WHITE_BALANCE, "5700"));

        int currentAeCompensation = Integer.parseInt(prefs2.getString(AE_COMPENSATION, "0"));
        Rect sensorCrop = getSensorRect(prefs2, currentZoomRatio, currentCameraCharacteristics.size);
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

