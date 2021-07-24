package org.fao.mobile.woodidentifier;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RecalibrateCameraActivity extends BaseCamera2Activity implements Slider.OnChangeListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = RecalibrateCameraActivity.class.getCanonicalName();
    Executor executor = Executors.newSingleThreadExecutor();

    private View capureButton;
    private Slider zoomControl;
    private SharedPreferences prefs;
    private Spinner lensSelector;
    private EditText whiteBalanceField;
    private Slider whiteBalanceControl;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recalibrate_camera);

        this.zoomControl = (Slider) findViewById(R.id.zoom_control);
        this.lensSelector = (Spinner) findViewById(R.id.lens_selector);
        this.whiteBalanceControl = (Slider) findViewById(R.id.white_balance_control);
        this.whiteBalanceField = (EditText) findViewById(R.id.white_balance_value);
        this.lensSelector.setOnItemSelectedListener(this);
        zoomControl.addOnChangeListener(this);
        whiteBalanceControl.addOnChangeListener(this);
        this.prefs = this.getSharedPreferences(
                "camera_settings", Context.MODE_PRIVATE);
        zoomControl.setValue(prefs.getFloat("zoom", (float) 0f));
        whiteBalanceField.setText(Long.toString(prefs.getInt("white_balance", 0)));
    }

    @Override
    protected void onSetupCameraComplete(ArrayList<CameraProperties> backFacingCameras) {
        super.onSetupCameraComplete(backFacingCameras);
        ArrayAdapter<CameraProperties> adapter =
                new ArrayAdapter<CameraProperties>(getApplicationContext(),  android.R.layout.simple_spinner_dropdown_item, backFacingCameras);
        adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item);
        lensSelector.setAdapter(adapter);
    }

    @Override
    public void onValueChange(Slider slider, float value, boolean fromUser) {
        float sliderValue = slider.getValue();
        Log.d(TAG, "onValue Change " + sliderValue);
        if (!fromUser) return;

        if (slider.getId() == R.id.zoom_control) {
            try {
                setCameraZoom(sliderValue);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else if (slider.getId() == R.id.white_balance_control) {
            try {
                setCameraWhiteBalance(sliderValue);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }
    }

    private void setCameraWhiteBalance(float sliderValue) throws CameraAccessException {
        if (currentCameraCharacteristics != null) {
            int colorTemp = (int)sliderValue;
            Log.d(TAG, "changing white balance to " + colorTemp);
            SharedPreferences prefs = this.getSharedPreferences(
                    "camera_settings", Context.MODE_PRIVATE);
            float zoomRatio = prefs.getFloat("zoom", 0f);
            prefs.edit().putInt("white_balance", colorTemp).commit();

            CaptureRequest.Builder captureRequest = prepareCameraSettings(CameraDevice.TEMPLATE_PREVIEW, 0, colorTemp, zoomRatio);
            captureRequest.addTarget(holder.getSurface());
            session.setRepeatingRequest(captureRequest.build(), null, null);
        }
    }

    protected void setCameraZoom(float sliderValue) throws CameraAccessException {
        if (currentCameraCharacteristics != null) {
            Range<Float> zoomRange = currentCameraCharacteristics.zoomRatioRange;
            float zoomRatio = (zoomRange.getUpper() - zoomRange.getLower()) * sliderValue + zoomRange.getLower();
            Log.d(TAG, "changing zoom to " + zoomRatio);
            SharedPreferences prefs = this.getSharedPreferences(
                    "camera_settings", Context.MODE_PRIVATE);
            int colorTemp = prefs.getInt("white_balance", 5700);
            prefs.edit().putFloat("zoom", sliderValue).commit();

            CaptureRequest.Builder captureRequest = prepareCameraSettings(CameraDevice.TEMPLATE_PREVIEW, 0, colorTemp, zoomRatio);
            captureRequest.addTarget(holder.getSurface());
            session.setRepeatingRequest(captureRequest.build(), null, null);
        }
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

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        CameraProperties properties = (CameraProperties)parent.getAdapter().getItem(position);
        try {
            setupCamera(holder, properties);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}