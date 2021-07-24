package org.fao.mobile.woodidentifier;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import org.fao.mobile.woodidentifier.utils.Utils;

public class FirstRunActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_run);
    }

    public void startCalibration(View view) {
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        if (PackageManager.PERMISSION_GRANTED != permission) {
            Utils.verifyCameraPermissions(this);
        } else {
            Intent intent = new Intent(this, RecalibrateCameraActivity.class);
            startActivity(intent);
        }
    }
}