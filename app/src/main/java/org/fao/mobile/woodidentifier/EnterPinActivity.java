package org.fao.mobile.woodidentifier;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;

import org.fao.mobile.woodidentifier.models.User;
import org.fao.mobile.woodidentifier.utils.SharedPrefsUtil;

public class EnterPinActivity extends AppCompatActivity {

    private static final int PIN_CODE = 1;
    private EditText pinCodeField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_pin);

        User user = SharedPrefsUtil.getUserInfo(this);
        this.pinCodeField = (EditText)findViewById(R.id.pinCodeField);
        pinCodeField.requestFocus();
        pinCodeField.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 4) {
                    if (s.toString().equals(user.getPinCode())) {
                        Intent intent = new Intent();
                        setResult(1, intent);
                        finish();
                    } else {
                        pinCodeField.requestFocus();
                        pinCodeField.setError("Invalid Pin Code");
                    }
                }
            }
        });
    }

    public void onBackPressed() {
        Intent intent = new Intent();
        setResult(0, intent);
        finish();
    }
}