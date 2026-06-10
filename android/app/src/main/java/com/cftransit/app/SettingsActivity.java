package com.cftransit.app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.cftransit.app.transit.Transit;

public class SettingsActivity extends AppCompatActivity {

    private EditText editBaseUrl;
    private EditText editApiKey;
    private Button btnTestConnection;
    private Button btnSave;
    private TextView txtTestResult;

    private static final String PREFS_NAME = "cftransit_prefs";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editBaseUrl = findViewById(R.id.editBaseUrl);
        editApiKey = findViewById(R.id.editApiKey);
        btnTestConnection = findViewById(R.id.btnTestConnection);
        btnSave = findViewById(R.id.btnSave);
        txtTestResult = findViewById(R.id.txtTestResult);

        loadSettings();

        btnTestConnection.setOnClickListener(v -> testConnection());
        btnSave.setOnClickListener(v -> saveAndFinish());
    }

    private void loadSettings() {
        String url = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("api_base_url", "");
        String key = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("api_key", "");
        editBaseUrl.setText(url);
        editApiKey.setText(key);
    }

    private void testConnection() {
        String url = editBaseUrl.getText().toString().trim();
        String key = editApiKey.getText().toString().trim();

        if (url.isEmpty()) {
            showTestResult("请输入 API 地址");
            return;
        }

        Transit.setAPIConfig(url, key);
        btnTestConnection.setEnabled(false);
        btnTestConnection.setText("测试中...");
        showTestResult("正在连接...");

        executor.execute(() -> {
            String result = Transit.testConnection();
            runOnUiThread(() -> {
                btnTestConnection.setEnabled(true);
                btnTestConnection.setText("测试连接");
                try {
                    JSONObject json = new JSONObject(result);
                    boolean ok = json.optBoolean("ok", false);
                    if (ok) {
                        showTestResult("连接成功");
                    } else {
                        showTestResult("连接失败: " + json.optString("error", "未知错误"));
                    }
                } catch (Exception e) {
                    showTestResult("解析结果失败");
                }
            });
        });
    }

    private void showTestResult(String text) {
        txtTestResult.setVisibility(View.VISIBLE);
        txtTestResult.setText(text);
    }

    private void saveAndFinish() {
        String url = editBaseUrl.getText().toString().trim();
        String key = editApiKey.getText().toString().trim();

        // 移除末尾斜杠
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString("api_base_url", url)
                .putString("api_key", key)
                .apply();

        Transit.setAPIConfig(url, key);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
