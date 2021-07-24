package org.fao.mobile.woodidentifier;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewStub;

public class ImageCaptureActivity2 extends BaseCamera2Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_capture2);
    }

    @Override
    protected View getCaptureButton() {
        return findViewById(R.id.fab_take_picture);
    }

    protected SurfaceView getCameraPreviewTextureView() {
        return (SurfaceView) ((ViewStub) findViewById(R.id.image_classification_texture_view_stub))
                .inflate()
                .findViewById(R.id.image_capture_surface_view);
    }
}