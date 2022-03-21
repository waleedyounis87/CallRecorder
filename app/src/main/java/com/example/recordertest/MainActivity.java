package com.example.recordertest;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String LOGTAG = "callRecorderLog";
    TextView pathText;

    private final ActivityResultLauncher<Intent> accessibilityServiceResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (!isAccessibilityServiceEnabled()) {
                    requestAccessibilityService();
                } else {
                    RunRecorder();
                }
            });

    private final ActivityResultLauncher<Intent> configurationResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    startRecorderService();
                }
            });

    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + RecordingAccessibilityService.class.getCanonicalName();

        try {
            accessibilityEnabled = Settings.Secure.getInt(getApplicationContext().getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(LOGTAG, "Error finding setting, default accessibility to not found: "
                    + e.getMessage());
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();

                    if (accessibilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }

        return false;

    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pathText = findViewById(R.id.pathText);
        pathText.setText(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Records");

        String[] permissions = {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        ArrayList<String> permissionsToAskFor = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
                permissionsToAskFor.add(permission);
            }
        }

        if (permissionsToAskFor.size() > 0) {
            ActivityCompat.requestPermissions(MainActivity.this, permissionsToAskFor.toArray(new String[0]), 1);
        }

        if (!isAccessibilityServiceEnabled()) {
            requestAccessibilityService();
        } else {
            RunRecorder();
        }
    }

    private void RunRecorder() {
        startRecorderService();
        //startConfig();
        //SharedPreferences apiDetails = getSharedPreferences("apiDetails", Context.MODE_PRIVATE);
        //String url = apiDetails.getString("url", "");
        //String username = apiDetails.getString("username", "");
        //String token = apiDetails.getString("token", "");

        /*if (!TextUtils.isEmpty(url) && !TextUtils.isEmpty(username) && !TextUtils.isEmpty(token)) {
            CustomProgressDialog.show(this, "Checking connection...");

            //EspoAPI checkConnectionAPI = new EspoAPI(Uri.parse(url), username, token);


        } else {
            startConfig();
        }*/
    }

   /* private void startConfig() {
        configurationResultLauncher.launch(new Intent(this, ConfigurationActivity.class));
    }*/

    private void startRecorderService() {
        startService(new Intent(this, CallService.class));
        setContentView(R.layout.activity_main);
    }

    private void requestAccessibilityService() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.enable_service_dialog_title)
                .setMessage(R.string.enable_service_dialog_desc)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    accessibilityServiceResultLauncher.launch(intent);
                })
                .show();
    }
}