package org.fao.mobile.woodidentifier;

import static android.text.TextUtils.isEmpty;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import org.fao.mobile.woodidentifier.models.User;
import org.fao.mobile.woodidentifier.utils.PhoneAutoConfig;
import org.fao.mobile.woodidentifier.utils.SharedPrefsUtil;

public class RegistrationActivity extends AppCompatActivity implements View.OnClickListener {

    private View saveButton;
    private EditText firstName;
    private EditText lastName;
    private EditText pinCode;
    private EditText pinCodeConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);
        this.saveButton = findViewById(R.id.saveButton);
        this.firstName = (EditText) findViewById(R.id.editTextTextPersonFirstName);
        this.lastName = (EditText) findViewById(R.id.editTextTextPersonLastName);
        this.pinCode = (EditText) findViewById(R.id.editTextTextPinCode);
        this.pinCodeConfirm = (EditText) findViewById(R.id.editTextTextPinCodeConfirm);
        saveButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.saveButton:
                if (!validateFields()) {
                    User user = new User();
                    user.setFirstName(this.firstName.getText().toString());
                    user.setLastName(this.lastName.getText().toString());
                    user.setPinCode(this.pinCode.getText().toString());
                    SharedPrefsUtil.saveUserInfo(this, user);

                    boolean autoConfigureStatus;
                    String phoneId = Build.MANUFACTURER + "-" + Build.MODEL;

                    if (SharedPrefsUtil.isFirstRun(this)) {
                        SharedPrefsUtil.setFirstRun(this);
                        autoConfigureStatus = PhoneAutoConfig.setPhoneSettingsFor(this, phoneId);
                    } else {
                        autoConfigureStatus = true;
                    }

                    if (!autoConfigureStatus) {
                        Intent calibrationIntent = new Intent(this, FirstRunActivity.class);
                        startActivity(calibrationIntent);
                    }
                    finish();
                }
                break;
        }
    }

    private boolean validateFields() {
        boolean isError = false;
        if (isEmpty(firstName.getText())) {
            firstName.requestFocus();
            firstName.setError("First Name is missing");
            isError = true;
        }
        if (isEmpty(lastName.getText())) {
            lastName.requestFocus();
            lastName.setError("Last Name is missing");
            isError = true;
        }

        if (isEmpty(pinCode.getText())) {
            pinCode.requestFocus();
            pinCode.setError("Please enter a pin code");
            isError = true;
        } else if (pinCode.getText().length() < 4) {
            pinCode.requestFocus();
            pinCode.setError("PIN code must be 4 characters");
            isError = true;
        }

        if (isEmpty(pinCodeConfirm.getText())) {
            pinCodeConfirm.requestFocus();
            pinCodeConfirm.setError("Please re-enter pin code");
            isError = true;
        }

        if (pinCodeConfirm.getText().equals(pinCode.getText())) {
            pinCodeConfirm.requestFocus();
            pinCodeConfirm.setError("PIN codes entered are not the same, please check.");
            isError = true;
        }
        return isError;
    }
}