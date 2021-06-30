package org.fao.mobile.woodidentifier;

import android.content.Context;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import org.fao.mobile.woodidentifier.utils.ModelHelper;

public class IdentifySampleActivity extends AbstractCameraXActivity {
    @Override
    protected int getContentViewLayoutId() {
        return R.layout.identify_sample;
    }

    @Override
    protected PreviewView getCameraPreviewTextureView() {
        return null;
    }

    @Override
    protected void afterOnCreate() {
        LocationManager locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.identify_sample);
    }

    @Override
    protected ModelHelper.Result analyzeImage(ImageProxy image, int rotationDegrees) {
        return null;
    }

    @Override
    protected void applyToUiAnalyzeImageResult(ModelHelper.Result result) {

    }

    @Override
    protected void afterExposureSet(float value) {

    }

    @Override
    protected void afterZoomSet(float value) {

    }
}