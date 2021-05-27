package org.fao.mobile.woodidentifier;

import android.view.ViewStub;

import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import org.fao.mobile.woodidentifier.utils.ModelHelper;

public class ImageCaptureActivity extends AbstractCameraXActivity {
    @Override
    protected int getContentViewLayoutId() {
        return R.layout.activity_image_classification;
    }

    @Override
    protected PreviewView getCameraPreviewTextureView() {
        return (PreviewView)((ViewStub) findViewById(R.id.image_classification_texture_view_stub))
                .inflate()
                .findViewById(R.id.image_classification_texture_view);
    }

    @Override
    protected ModelHelper.Result analyzeImage(ImageProxy image, int rotationDegrees) {
        return null;
    }

    @Override
    protected void applyToUiAnalyzeImageResult(ModelHelper.Result result) {

    }

    public class
    AnalysisResult {
    }
}
