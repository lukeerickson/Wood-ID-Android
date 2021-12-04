package org.fao.mobile.woodidentifier;

import androidx.appcompat.app.AppCompatActivity;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import org.fao.mobile.woodidentifier.utils.ModelHelper;

public class AboutActivity extends AppCompatActivity {
    private TextView versionTextView;
    private TextView modeVersionTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        versionTextView = (TextView)findViewById(R.id.textViewVersion);
        modeVersionTextView = (TextView)findViewById(R.id.textModelVersion);

        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String version = pInfo.versionName;

        versionTextView.setText("Application Version: " + version);
        modeVersionTextView.setText("Model Version: " + ModelHelper.getHelperInstance(this).getVersion());
    }


}