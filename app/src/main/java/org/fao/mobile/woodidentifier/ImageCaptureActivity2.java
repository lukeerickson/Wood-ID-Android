package org.fao.mobile.woodidentifier;

import androidx.annotation.RequiresApi;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewStub;
import android.widget.RelativeLayout;

import org.fao.mobile.woodidentifier.views.AutoFitTextureView;

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
    protected View[] getCaptureButton() {
        return new View[] { findViewById(R.id.fab_take_picture), findViewById(R.id.fab_take_picture2)};
    }

    @Override
    protected View getCancelButton() {
        return findViewById(R.id.fab_cancel);
    }

    protected AutoFitTextureView getCameraPreviewTextureView() {
        return (AutoFitTextureView) ((ViewStub) findViewById(R.id.image_classification_texture_view_stub))
                .inflate()
                .findViewById(R.id.image_capture_surface_view);
    }

    @Override
    protected void onCameraFrameSet(View cameraFrame, int autoCrop) {
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(autoCrop, autoCrop);
        relativeParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        cameraFrame.setLayoutParams(relativeParams);
    }

    @Override
    protected void onSetupCameraComplete(ArrayList<CameraProperties> backFacingCameras, CameraProperties camera) throws CameraAccessException {
        super.onSetupCameraComplete(backFacingCameras, camera);
    }
}