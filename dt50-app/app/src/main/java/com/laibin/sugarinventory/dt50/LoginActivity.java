package com.laibin.sugarinventory.dt50;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends Activity {
    private AppPreferences preferences;
    private EditText nameInput;
    private EditText passwordInput;
    private CheckBox rememberMeCheckBox;
    private Button loginButton;
    private TextView loginError;
    private LoginCredentialStore credentialStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        if (!preferences.token().trim().isEmpty()) {
            openWorkbench();
            return;
        }
        setContentView(R.layout.activity_login);
        nameInput = findViewById(R.id.nameInput);
        passwordInput = findViewById(R.id.passwordInput);
        rememberMeCheckBox = findViewById(R.id.rememberMeCheckBox);
        loginButton = findViewById(R.id.loginButton);
        loginError = findViewById(R.id.loginError);
        credentialStore = new LoginCredentialStore(this);
        restoreRememberedLogin();
        rememberMeCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                credentialStore.clear();
            }
        });
        loginButton.setOnClickListener(view -> login());
        passwordInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                login();
                return true;
            }
            return false;
        });
    }

    private void login() {
        String serverUrl = preferences.serverUrl();
        String name = nameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (name.trim().isEmpty() || password.trim().isEmpty()) {
            showError("请填写姓名和登录口令");
            return;
        }
        loginButton.setEnabled(false);
        loginButton.setText("正在登录…");
        loginError.setVisibility(View.GONE);
        new ApiClient(serverUrl, "").login(name, password, new ApiClient.Callback<>() {
            @Override
            public void onSuccess(ApiClient.LoginResult value) {
                preferences.saveSession(value.name, value.token, value.permissionCodes);
                if (rememberMeCheckBox.isChecked()
                        && !credentialStore.save(name, password)) {
                    Toast.makeText(
                            LoginActivity.this,
                            "登录成功，但本机未能保存登录信息",
                            Toast.LENGTH_LONG
                    ).show();
                }
                openWorkbench();
            }

            @Override
            public void onError(String message, int httpStatus) {
                loginButton.setEnabled(true);
                loginButton.setText("登录并进入工作台");
                showError(message);
            }
        });
    }

    private void restoreRememberedLogin() {
        LoginCredentialStore.Credentials remembered = credentialStore.load();
        if (remembered == null) {
            nameInput.setText(preferences.userName());
            return;
        }
        nameInput.setText(remembered.userName);
        passwordInput.setText(remembered.password);
        rememberMeCheckBox.setChecked(true);
    }

    private void showError(String message) {
        loginError.setText(message);
        loginError.setVisibility(View.VISIBLE);
    }

    private void openWorkbench() {
        startActivity(new Intent(this, WorkbenchActivity.class));
        finish();
    }
}
