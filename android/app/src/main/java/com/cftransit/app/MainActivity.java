package com.cftransit.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.cftransit.app.transit.Transit;

public class MainActivity extends AppCompatActivity {

    private EditText editBandwidth;
    private Button btnScan;
    private Button btnClearHistory;
    private ProgressBar progressBar;
    private TextView txtProgressTitle;
    private TextView txtProgress;
    private TextView txtResult;
    private TextView txtThemeMode;
    private TextView txtIpValue;
    private TextView txtPort;
    private TextView txtTLS;
    private TextView txtTargetBandwidth;
    private TextView txtRealBandwidth;
    private TextView txtMaxSpeed;
    private TextView txtLatency;
    private TextView txtDataCenter;
    private TextView txtElapsed;
    private TextView txtEmptyHistory;
    private TextView txtFilterStatus;
    private View layoutProgress;
    private View layoutResult;
    private LinearLayout layoutHistoryList;
    private TextView btnSettings;
    private Spinner spinnerCountry;
    private Spinner spinnerDC;
    private Spinner spinnerTLS;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Runnable progressPoller;
    private static Toast activeToast;
    private String currentIp = "";
    private int currentPort = 0;
    private boolean currentTLS = false;

    private static final String PREFS_NAME = "cftransit_prefs";
    private static final String PREFS_THEME = "theme_idx";
    private static final String PREFS_HISTORY = "history_records";
    private static final String PREFS_BANDWIDTH = "bandwidth";
    private static final String PREFS_COUNTRY = "filter_country";
    private static final String PREFS_DC = "filter_dc";
    private static final String PREFS_TLS = "filter_tls";
    private static final int MAX_HISTORY = 10;

    private static final String[] TLS_OPTIONS = {"全部", "仅 TLS", "仅非 TLS"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editBandwidth = findViewById(R.id.editBandwidth);
        btnScan = findViewById(R.id.btnScan);
        btnClearHistory = findViewById(R.id.btnClearHistory);
        progressBar = findViewById(R.id.progressBar);
        txtProgressTitle = findViewById(R.id.txtProgressTitle);
        txtProgress = findViewById(R.id.txtProgress);
        txtResult = findViewById(R.id.txtResult);
        layoutProgress = findViewById(R.id.layoutProgress);
        layoutResult = findViewById(R.id.layoutResult);
        txtThemeMode = findViewById(R.id.txtThemeMode);
        txtIpValue = findViewById(R.id.txtIpValue);
        txtPort = findViewById(R.id.txtPort);
        txtTLS = findViewById(R.id.txtTLS);
        txtTargetBandwidth = findViewById(R.id.txtTargetBandwidth);
        txtRealBandwidth = findViewById(R.id.txtRealBandwidth);
        txtMaxSpeed = findViewById(R.id.txtMaxSpeed);
        txtLatency = findViewById(R.id.txtLatency);
        txtDataCenter = findViewById(R.id.txtDataCenter);
        txtElapsed = findViewById(R.id.txtElapsed);
        txtEmptyHistory = findViewById(R.id.txtEmptyHistory);
        layoutHistoryList = findViewById(R.id.layoutHistoryList);
        btnSettings = findViewById(R.id.btnSettings);
        txtFilterStatus = findViewById(R.id.txtFilterStatus);
        spinnerCountry = findViewById(R.id.spinnerCountry);
        spinnerDC = findViewById(R.id.spinnerDC);
        spinnerTLS = findViewById(R.id.spinnerTLS);

        Transit.setCacheDir(getFilesDir().getAbsolutePath());
        loadApiConfig();
        loadScanSettings();

        // TLS Spinner 固定选项
        ArrayAdapter<String> tlsAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TLS_OPTIONS);
        tlsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTLS.setAdapter(tlsAdapter);

        // 初始化筛选 Spinner 为空
        setSpinnerOptions(spinnerCountry, new ArrayList<>());
        setSpinnerOptions(spinnerDC, new ArrayList<>());

        // 恢复筛选设置
        int savedTLS = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREFS_TLS, 0);
        spinnerTLS.setSelection(savedTLS);

        // 主题
        themeModeIndex = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREFS_THEME, 0);
        applyTheme();
        updateThemeLabel();

        txtThemeMode.setOnClickListener(v -> cycleThemeMode());
        txtIpValue.setOnClickListener(v -> copyCurrentIp());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        editBandwidth.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                normalizeBandwidthInput();
                v.clearFocus();
                hideKeyboard(v);
                return true;
            }
            return false;
        });
        editBandwidth.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) normalizeBandwidthInput();
        });

        btnScan.setOnClickListener(v -> {
            if (isRunning.get()) {
                btnScan.setEnabled(false);
                btnScan.setText("正在停止...");
                txtProgress.setText("正在取消扫描...");
                Transit.cancelScan();
            } else {
                startScan();
            }
        });
        btnClearHistory.setOnClickListener(v -> clearScanHistory());

        renderHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApiConfig();
        loadFilterOptions();
    }

    private void loadApiConfig() {
        String url = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("api_base_url", "");
        String key = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("api_key", "");
        Transit.setAPIConfig(url, key);
    }

    private void loadFilterOptions() {
        String url = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("api_base_url", "");
        if (url.isEmpty()) return;

        txtFilterStatus.setVisibility(View.VISIBLE);
        txtFilterStatus.setText("正在加载筛选选项...");

        executor.execute(() -> {
            String result = Transit.fetchFilterOptions();
            runOnUiThread(() -> {
                try {
                    JSONObject json = new JSONObject(result);
                    if (json.has("error")) {
                        txtFilterStatus.setText("加载失败: " + json.getString("error"));
                        return;
                    }

                    List<String> dcList = jsonArrayToList(json.optJSONArray("dcList"));
                    List<String> countryList = jsonArrayToList(json.optJSONArray("countryList"));

                    setSpinnerOptions(spinnerCountry, countryList);
                    setSpinnerOptions(spinnerDC, dcList);

                    // 恢复之前的选择
                    String savedCountry = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREFS_COUNTRY, "");
                    String savedDC = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREFS_DC, "");
                    selectSpinnerValue(spinnerCountry, savedCountry);
                    selectSpinnerValue(spinnerDC, savedDC);

                    txtFilterStatus.setText(String.format(Locale.getDefault(),
                            "已加载 %d 个国家, %d 个数据中心", countryList.size(), dcList.size()));
                } catch (Exception e) {
                    txtFilterStatus.setText("解析筛选选项失败");
                }
            });
        });
    }

    private void setSpinnerOptions(Spinner spinner, List<String> options) {
        List<String> items = new ArrayList<>();
        items.add("全部");
        items.addAll(options);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void selectSpinnerValue(Spinner spinner, String value) {
        if (value == null || value.isEmpty()) {
            spinner.setSelection(0);
            return;
        }
        @SuppressWarnings("unchecked")
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private List<String> jsonArrayToList(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "");
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    private String getSelectedCountry() {
        String val = spinnerCountry.getSelectedItem().toString();
        return val.equals("全部") ? "" : val;
    }

    private String getSelectedDC() {
        String val = spinnerDC.getSelectedItem().toString();
        return val.equals("全部") ? "" : val;
    }

    private boolean getTLSOnly() {
        int idx = spinnerTLS.getSelectedItemPosition();
        return idx == 1; // "仅 TLS"
    }

    private void startScan() {
        if (isRunning.get()) return;

        String url = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("api_base_url", "");
        if (url.isEmpty()) {
            showToast("请先在设置中配置 API 地址");
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        final int bandwidth = normalizeBandwidthInput();
        final String country = getSelectedCountry();
        final String dc = getSelectedDC();
        final boolean tlsOnly = getTLSOnly();

        editBandwidth.clearFocus();
        hideKeyboard(editBandwidth);
        saveScanSettings();

        btnScan.setText("停止扫描");
        btnScan.setBackgroundResource(R.drawable.btn_danger_bg);
        btnScan.setEnabled(true);
        currentIp = "";

        showScanning();
        showProgressText("正在初始化...");
        btnClearHistory.setEnabled(false);
        isRunning.set(true);
        startProgressPolling();

        executor.execute(() -> {
            try {
                String resultJson = Transit.getIPs(bandwidth, country, dc, tlsOnly);
                mainHandler.post(() -> onScanResult(resultJson));
            } catch (Exception e) {
                mainHandler.post(() -> showResult("扫描出错: " + e.getMessage()));
            }
        });
    }

    private void startProgressPolling() {
        stopProgressPolling();
        progressPoller = new Runnable() {
            @Override
            public void run() {
                if (!isRunning.get()) return;
                try {
                    String p = Transit.getProgress();
                    if (p != null && !p.isEmpty()) txtProgress.setText(p);
                } catch (Exception ignored) {}
                mainHandler.postDelayed(this, 500);
            }
        };
        mainHandler.postDelayed(progressPoller, 500);
    }

    private void stopProgressPolling() {
        if (progressPoller != null) {
            mainHandler.removeCallbacks(progressPoller);
            progressPoller = null;
        }
    }

    private void onScanResult(String resultJson) {
        stopProgressPolling();
        layoutProgress.setVisibility(View.GONE);
        isRunning.set(false);
        resetButtons();

        if (resultJson == null || resultJson.isEmpty()) {
            txtProgress.setText("扫描已取消");
            return;
        }

        try {
            JSONObject json = new JSONObject(resultJson);
            String ip = json.optString("ip", "");
            String error = json.optString("error", "");

            if (ip.isEmpty() && error.isEmpty()) {
                txtProgress.setText("扫描已取消");
                return;
            }

            if (!ip.isEmpty()) {
                int port = json.optInt("port", 0);
                boolean tls = json.optBoolean("tls", false);
                int bw = json.optInt("bandwidth", 0);
                int realBw = json.optInt("realBandwidth", 0);
                int maxSpeed = json.optInt("maxSpeed", 0);
                int latencyMs = json.optInt("latencyMs", 0);
                String dcVal = json.optString("dc", "");
                int elapsed = json.optInt("elapsed", 0);
                currentIp = ip;
                currentPort = port;
                currentTLS = tls;

                String scanTime = formatNow();
                showStructuredResult(ip, port, tls, bw, realBw, maxSpeed, latencyMs, dcVal, elapsed);
                saveHistory(scanTime, json);
                renderHistory();
            } else {
                showResult(error);
            }
        } catch (Exception e) {
            showResult("解析结果失败: " + e.getMessage());
        }
    }

    private void resetButtons() {
        btnScan.setText("开始扫描");
        btnScan.setBackgroundResource(R.drawable.btn_primary_bg);
        btnScan.setEnabled(true);
        btnClearHistory.setEnabled(true);
    }

    private void showScanning() {
        txtProgressTitle.setText("执行状态");
        layoutProgress.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        layoutResult.setVisibility(View.GONE);
    }

    private void showProgressText(String text) {
        txtProgress.setVisibility(View.VISIBLE);
        txtProgress.setText(text);
    }

    private void showResult(String text) {
        layoutProgress.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
        txtIpValue.setText("未找到可用 IP");
        txtIpValue.setEnabled(false);
        txtPort.setText("-");
        txtTLS.setText("-");
        txtTargetBandwidth.setText("-");
        txtRealBandwidth.setText("-");
        txtMaxSpeed.setText("-");
        txtLatency.setText("-");
        txtDataCenter.setText("-");
        txtElapsed.setText("-");
        currentIp = "";
        currentPort = 0;
        txtResult.setVisibility(View.VISIBLE);
        txtResult.setText(text);
    }

    private void showStructuredResult(String ip, int port, boolean tls, int bw, int realBw, int maxSpeed, int latencyMs, String dc, int elapsed) {
        layoutProgress.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
        txtIpValue.setEnabled(true);
        txtIpValue.setText(ip);
        txtPort.setText(String.valueOf(port));
        txtTLS.setText(tls ? "是" : "否");
        txtTargetBandwidth.setText(bw + " Mbps");
        txtRealBandwidth.setText(realBw + " Mbps");
        txtMaxSpeed.setText(maxSpeed + " kB/s");
        txtLatency.setText(latencyMs + " ms");
        txtDataCenter.setText(displayValue(dc));
        txtElapsed.setText(elapsed + " 秒");
        txtResult.setVisibility(View.GONE);
    }

    // ---- 带宽 ----
    private int parseBandwidth() {
        try {
            int val = Integer.parseInt(editBandwidth.getText().toString().trim());
            return val <= 0 ? 1 : val;
        } catch (NumberFormatException e) { return 1; }
    }

    private int normalizeBandwidthInput() {
        int bw = parseBandwidth();
        String s = String.valueOf(bw);
        if (!s.equals(editBandwidth.getText().toString())) {
            editBandwidth.setText(s);
            editBandwidth.setSelection(s.length());
        }
        return bw;
    }

    private void loadScanSettings() {
        int bw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREFS_BANDWIDTH, 1);
        editBandwidth.setText(String.valueOf(bw <= 0 ? 1 : bw));
    }

    private void saveScanSettings() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putInt(PREFS_BANDWIDTH, normalizeBandwidthInput())
                .putString(PREFS_COUNTRY, getSelectedCountry())
                .putString(PREFS_DC, getSelectedDC())
                .putInt(PREFS_TLS, spinnerTLS.getSelectedItemPosition())
                .apply();
    }

    // ---- 主题 ----
    private static int themeModeIndex = 0;
    private static final String[] THEME_LABELS = {"🌓", "☀️", "🌙"};

    private void applyTheme() {
        switch (themeModeIndex) {
            case 1: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); break;
            case 2: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); break;
            default: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); break;
        }
    }

    private void updateThemeLabel() { txtThemeMode.setText(THEME_LABELS[themeModeIndex]); }

    private void cycleThemeMode() {
        themeModeIndex = (themeModeIndex + 1) % 3;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(PREFS_THEME, themeModeIndex).apply();
        applyTheme();
        updateThemeLabel();
        showToast("主题: " + THEME_LABELS[themeModeIndex]);
    }

    // ---- 剪贴板 ----
    private void copyCurrentIp() {
        if (currentIp == null || currentIp.isEmpty()) { showToast("暂无可复制的 IP"); return; }
        String copyText = currentIp + ":" + currentPort;
        copyToClipboard("CF-IP", copyText, "已复制: " + copyText + (currentTLS ? " (TLS)" : ""));
    }

    private void copyToClipboard(String label, String text, String toastText) {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText(label, text));
        showToast(toastText);
    }

    private void showToast(String msg) {
        if (activeToast != null) activeToast.cancel();
        activeToast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        activeToast.show();
    }

    // ---- 历史记录 ----
    private void saveHistory(String scanTime, JSONObject source) {
        try {
            JSONObject item = new JSONObject();
            item.put("time", scanTime);
            item.put("ip", source.optString("ip", ""));
            item.put("port", source.optInt("port", 0));
            item.put("tls", source.optBoolean("tls", false));
            item.put("bandwidth", source.optInt("bandwidth", 0));
            item.put("realBandwidth", source.optInt("realBandwidth", 0));
            item.put("maxSpeed", source.optInt("maxSpeed", 0));
            item.put("latencyMs", source.optInt("latencyMs", 0));
            item.put("dc", source.optString("dc", ""));
            item.put("elapsed", source.optInt("elapsed", 0));

            JSONArray next = new JSONArray();
            next.put(item);
            JSONArray old = loadHistory();
            for (int i = 0; i < old.length() && next.length() < MAX_HISTORY; i++) {
                next.put(old.getJSONObject(i));
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(PREFS_HISTORY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    private JSONArray loadHistory() {
        String raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREFS_HISTORY, "[]");
        try { return new JSONArray(raw); } catch (Exception e) { return new JSONArray(); }
    }

    private void renderHistory() {
        layoutHistoryList.removeAllViews();
        JSONArray history = loadHistory();
        txtEmptyHistory.setVisibility(history.length() == 0 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item != null) layoutHistoryList.addView(createHistoryItem(item, i));
        }
    }

    private void clearScanHistory() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(PREFS_HISTORY).apply();
        renderHistory();
        showToast("历史记录已清空");
    }

    private void deleteHistoryItem(int index) {
        JSONArray history = loadHistory();
        if (index < 0 || index >= history.length()) return;
        JSONArray next = new JSONArray();
        for (int i = 0; i < history.length(); i++) {
            if (i != index) {
                JSONObject item = history.optJSONObject(i);
                if (item != null) next.put(item);
            }
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(PREFS_HISTORY, next.toString()).apply();
        renderHistory();
        showToast("已删除该条历史记录");
    }

    private View createHistoryItem(JSONObject item, int index) {
        String time = item.optString("time", "");
        String ip = item.optString("ip", "");
        int port = item.optInt("port", 0);
        boolean tls = item.optBoolean("tls", false);
        int bw = item.optInt("bandwidth", 0);
        int realBw = item.optInt("realBandwidth", 0);
        int maxSpeed = item.optInt("maxSpeed", 0);
        int latencyMs = item.optInt("latencyMs", 0);
        String dc = item.optString("dc", "");
        int elapsed = item.optInt("elapsed", 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.metric_bg);
        root.setPadding(dp(12), dp(11), dp(12), dp(11));
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rootParams.setMargins(0, dp(8), 0, 0);
        root.setLayoutParams(rootParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView timeView = new TextView(this);
        timeView.setText(time);
        timeView.setTextColor(color(R.color.text_secondary));
        timeView.setTextSize(12);
        header.addView(timeView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView delBtn = new TextView(this);
        delBtn.setBackgroundResource(R.drawable.btn_secondary_bg);
        delBtn.setClickable(true);
        delBtn.setFocusable(true);
        delBtn.setGravity(Gravity.CENTER);
        delBtn.setText("删除");
        delBtn.setTextColor(color(R.color.danger));
        delBtn.setTextSize(12);
        delBtn.setOnClickListener(v -> deleteHistoryItem(index));
        header.addView(delBtn, new LinearLayout.LayoutParams(dp(48), dp(30)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView ipView = new TextView(this);
        String displayIp = ip + ":" + port + (tls ? " 🔒" : "");
        ipView.setText(displayIp);
        ipView.setTextColor(color(R.color.primary));
        ipView.setTextSize(18);
        ipView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        ipView.setGravity(Gravity.CENTER_VERTICAL);
        ipView.setMinHeight(dp(40));
        ipView.setPadding(0, dp(5), 0, dp(4));
        ipView.setClickable(true);
        String copyText = ip + ":" + port;
        ipView.setOnClickListener(v -> copyToClipboard("CF-IP", copyText, "已复制: " + copyText + (tls ? " (TLS)" : "")));
        root.addView(ipView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView details = new TextView(this);
        details.setText("实测 " + realBw + " Mbps / 目标 " + bw + " Mbps\n"
                + "峰值 " + maxSpeed + " kB/s / 延迟 " + latencyMs + " ms\n"
                + "DC " + displayValue(dc) + " / TLS " + (tls ? "是" : "否") + " / 用时 " + elapsed + " 秒");
        details.setTextColor(color(R.color.text_secondary));
        details.setTextSize(13);
        details.setLineSpacing(dp(2), 1.0f);
        root.addView(details, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return root;
    }

    private int color(int resId) { return getResources().getColor(resId, getTheme()); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private String formatNow() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()); }
    private String displayValue(String v) { return v == null || v.isEmpty() ? "-" : v; }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && getCurrentFocus() instanceof EditText) {
            View focused = getCurrentFocus();
            int[] loc = new int[2];
            focused.getLocationOnScreen(loc);
            int x = (int) ev.getRawX(), y = (int) ev.getRawY();
            if (x < loc[0] || x > loc[0] + focused.getWidth() || y < loc[1] || y > loc[1] + focused.getHeight()) {
                focused.clearFocus();
                hideKeyboard(focused);
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveScanSettings();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @Override
    public void onBackPressed() { exitApp(); }

    private void exitApp() {
        saveScanSettings();
        stopProgressPolling();
        if (isRunning.get()) Transit.cancelScan();
        if (activeToast != null) { activeToast.cancel(); activeToast = null; }
        executor.shutdownNow();
        finishAndRemoveTask();
        mainHandler.postDelayed(() -> {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        }, 150);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressPolling();
        executor.shutdownNow();
    }
}
