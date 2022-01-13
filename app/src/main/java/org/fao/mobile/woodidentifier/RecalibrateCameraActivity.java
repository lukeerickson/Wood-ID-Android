package org.fao.mobile.woodidentifier;

import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.CUSTOM_AWB;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.WHITE_BALANCE;
import static org.fao.mobile.woodidentifier.utils.SharedPrefsUtil.ZOOM;

import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.os.Build;
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
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

import com.google.android.material.slider.Slider;

import org.fao.mobile.woodidentifier.utils.SharedPrefsUtil;

import java.util.ArrayList;

public class RecalibrateCameraActivity extends BaseCamera2Activity implements Slider.OnChangeListener, AdapterView.OnItemSelectedListener, View.OnClickListener {
    private static final String TAG = RecalibrateCameraActivity.class.getCanonicalName();
    private View capureButton;
    private Slider zoomControl;
    private Spinner lensSelector;
    private EditText whiteBalanceField;
    private Slider whiteBalanceControl;
    private View startAutoWhiteBalance;
    private SharedPreferences prefs;
    private View preciseZoomAdd;
    private View preciseZoomMinus;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recalibrate_camera);
        this.zoomControl = (Slider) findViewById(R.id.zoom_control);
        this.lensSelector = (Spinner) findViewById(R.id.lens_selector);
        this.whiteBalanceControl = (Slider) findViewById(R.id.white_balance_control);
        this.whiteBalanceField = (EditText) findViewById(R.id.white_balance_value);
        this.startAutoWhiteBalance = findViewById(R.id.fab_awb_lock);
        this.preciseZoomAdd = findViewById(R.id.zoom_precise_plus);
        this.preciseZoomMinus = findViewById(R.id.zoom_precise_minus);

        zoomControl.addOnChangeListener(this);
        whiteBalanceControl.addOnChangeListener(this);
        startAutoWhiteBalance.setOnClickListener(this);
        preciseZoomAdd.setOnClickListener(this);
        preciseZoomMinus.setOnClickListener(this);


        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);


    }

    @Override
    protected void onSetupCameraComplete(ArrayList<CameraProperties> backFacingCameras, CameraProperties camera) throws CameraAccessException {
        super.onSetupCameraComplete(backFacingCameras, camera);
        ArrayAdapter<CameraProperties> adapter =
                new ArrayAdapter<CameraProperties>(getApplicationContext(), android.R.layout.simple_spinner_dropdown_item, backFacingCameras);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        lensSelector.setAdapter(adapter);
        lensSelector.setOnItemSelectedListener(this);
        Log.d(TAG, "Set default controls");
        if (currentCameraCharacteristics != null) {
            setDefaultZoomRatio();

        }
    }

    private void setDefaultZoomRatio() {
        float currentZoomRatio = Float.parseFloat(prefs.getString(ZOOM, "1.0"));
        Range<Float> zoomRange = currentCameraCharacteristics.zoomRatioRange;
        float sliderValue = (currentZoomRatio - zoomRange.getLower()) / (zoomRange.getUpper() - zoomRange.getLower());
        zoomControl.setValue(sliderValue);
    }

    @Override
    protected void onCameraConfigured(CameraProperties cameraProperties, CameraDevice camera) throws CameraAccessException {
        updateCameraState();
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
            int colorTemp = (int) sliderValue;
            Log.d(TAG, "changing white balance to " + colorTemp);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putString(WHITE_BALANCE, Integer.toString(colorTemp)).putBoolean(CUSTOM_AWB, false).apply();
            updateCameraState();
        }
    }


    protected void setCameraZoom(float sliderValue) throws CameraAccessException {
        if (currentCameraCharacteristics != null) {
            Range<Float> zoomRange = currentCameraCharacteristics.zoomRatioRange;
            float zoomRatio = (zoomRange.getUpper() - zoomRange.getLower()) * sliderValue + zoomRange.getLower();
            Log.d(TAG, "changing zoom to " + zoomRatio);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putString(ZOOM, Float.toString(zoomRatio)).apply();
            updateCameraState();
        }
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
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        CameraProperties properties = (CameraProperties) parent.getAdapter().getItem(position);
        try {
            if (currentCameraCharacteristics != null && !properties.equals(currentCameraCharacteristics)) {
                setupCamera(holder, properties);
            }
            SharedPrefsUtil.saveCurrentCamera(this, position);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.fab_awb_lock) {

            try {
                acquireAWBLock();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        } else if (v.getId() == R.id.zoom_precise_minus) {
            float sliderValue = (float) Math.max(0f, zoomControl.getValue() - 0.01);
            zoomControl.setValue(sliderValue);
            try {
                setCameraZoom(sliderValue);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else if (v.getId() == R.id.zoom_precise_plus) {
            float sliderValue = (float) Math.min(1f, zoomControl.getValue() + 0.01);
            zoomControl.setValue(sliderValue);
            try {
                setCameraZoom(sliderValue);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

}