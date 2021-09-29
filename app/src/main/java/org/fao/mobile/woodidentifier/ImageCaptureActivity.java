package org.fao.mobile.woodidentifier;

import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.ZOOM;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewStub;
import android.widget.EditText;

import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import com.google.android.material.slider.Slider;

import org.fao.mobile.woodidentifier.utils.ModelHelper;

public class ImageCaptureActivity extends AbstractCameraXActivity implements Slider.OnChangeListener {
    private Slider zoomControl;
    private Slider exposureControl;
    private EditText zoomValue;
    private EditText exposureValue;

    @Override
    protected int getContentViewLayoutId() {
        return R.layout.activity_image_classification;
    }

    @Override
    protected PreviewView getCameraPreviewTextureView() {
        return (PreviewView) ((ViewStub) findViewById(R.id.image_classification_texture_view_stub))
                .inflate()
                .findViewById(R.id.image_classification_texture_view);
    }

    @Override
    protected void afterOnCreate() {
        this.zoomControl = (Slider) findViewById(R.id.zoom_control);
        this.exposureControl = (Slider) findViewById(R.id.exposure_control);
        this.zoomValue = (EditText) findViewById(R.id.zoom_value);
        this.exposureValue = (EditText) findViewById(R.id.exposure_value);

        SharedPreferences prefs = this.getSharedPreferences(
                "camera_settings", Context.MODE_PRIVATE);
        zoomControl.setValue(prefs.getFloat(ZOOM, (float) 0f));
        exposureControl.setValue(prefs.getFloat("exposure", 0f));
        exposureValue.setText(Integer.toString((int) (prefs.getFloat("exposure", 0f) * 100f)));
        zoomValue.setText(Integer.toString((int) (prefs.getFloat(ZOOM, 0f) * 100f)));

        zoomControl.addOnChangeListener(this);
        exposureControl.addOnChangeListener(this);
        exposureValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                float manualSliderValue = Float.parseFloat(s.toString());
                exposureControl.setValue(manualSliderValue / 100f);
            }
        });
        zoomValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                float manualSliderValue = Float.parseFloat(s.toString());
                zoomControl.setValue(manualSliderValue / 100f);
            }
        });
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
        if (!exposureValue.getText().equals(Float.toString(value))) {
            exposureValue.setText(Float.toString((int) (value * 100f)));
        }
    }

    @Override
    protected void afterZoomSet(float value) {
        if (!zoomValue.getText().equals(Float.toString(value))) {
            zoomValue.setText(Float.toString((int) (value * 100f)));
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void onValueChange(Slider slider, float value, boolean fromUser) {
        float sliderValue = slider.getValue();
        if (slider.getId() == R.id.zoom_control) {
            setCameraZoom(sliderValue);
            SharedPreferences prefs = this.getSharedPreferences(
                    "camera_settings", Context.MODE_PRIVATE);
            prefs.edit().putFloat(ZOOM, sliderValue).commit();
        } else if (slider.getId() == R.id.exposure_control) {
            setCameraExposure(sliderValue);
            SharedPreferences prefs = this.getSharedPreferences(
                    "camera_settings", Context.MODE_PRIVATE);
            prefs.edit().putFloat("exposure", sliderValue).commit();
        }
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    public class
    AnalysisResult {
    }
}
