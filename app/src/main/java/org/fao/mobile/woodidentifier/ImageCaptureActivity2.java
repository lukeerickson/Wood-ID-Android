package org.fao.mobile.woodidentifier;

import androidx.annotation.RequiresApi;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewStub;

import java.util.ArrayList;

public class ImageCaptureActivity2 extends BaseCamera2Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_capture2);
    }

    @Override
    protected void onCameraConfigured(CameraProperties cameraProperties, CameraDevice camera) throws CameraAccessException {
        updateCameraState();
    }

    @Override
    protected View getCaptureButton() {
        return findViewById(R.id.fab_take_picture);
    }

    @Override
    protected View getCancelButton() {
        return findViewById(R.id.fab_cancel);
    }

    protected SurfaceView getCameraPreviewTextureView() {
        return (SurfaceView) ((ViewStub) findViewById(R.id.image_classification_texture_view_stub))
                .inflate()
                .findViewById(R.id.image_capture_surface_view);
    }

    @Override
    protected void onSetupCameraComplete(ArrayList<CameraProperties> backFacingCameras, CameraProperties camera) throws CameraAccessException {
        super.onSetupCameraComplete(backFacingCameras, camera);
    }
}