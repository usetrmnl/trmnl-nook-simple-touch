package com.bpmct.trmnl_nook_simple_touch;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public class SettingsActivity extends Activity {
    private static final int APP_ROTATION_DEGREES = 90;
    private TextView statusView;
    private CheckBox allowSleepCheck;
    private CheckBox fileLoggingCheck;
    private CheckBox giftModeCheck;
    private Button giftSettingsButton;
    private TextView sleepHint;
    private CheckBox allowHttpCheck;
    private CheckBox allowSelfSignedCheck;
    private CheckBox autoDisableWifiCheck;
    private TextView wifiStatusView;
    private FrameLayout rootLayout;
    private FrameLayout outerRoot;
    private View flashOverlay;
    private final Handler handler = new Handler();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));
        rootLayout.setBackgroundColor(0xFFFFFFFF);

        ScrollView scroll = new ScrollView(this);
        
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(24, 20, 24, 20);

        // Title
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(18);
        title.setTextColor(0xFF000000);
        main.addView(title);

        // ── Tab bar ──────────────────────────────────────────────────────────
        final LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabBarParams.topMargin = 12;

        final Button tabGeneral = createTabButton("General");
        final Button tabNetwork = createTabButton("Network");
        final Button tabSystem  = createTabButton("System");

        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tabBar.addView(tabGeneral, tabParams);
        tabBar.addView(tabNetwork, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tabBar.addView(tabSystem,  new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        main.addView(tabBar, tabBarParams);

        // ── Panel: General ───────────────────────────────────────────────────
        final LinearLayout panelGeneral = new LinearLayout(this);
        panelGeneral.setOrientation(LinearLayout.VERTICAL);

        panelGeneral.addView(createSectionLabel("Credentials"));
        statusView = new TextView(this);
        statusView.setTextSize(12);
        statusView.setTextColor(0xFF444444);
        statusView.setText(ApiPrefs.hasCredentials(this) ? "Configured" : "Not set");
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = 6;
        panelGeneral.addView(statusView, statusParams);

        Button editButton = createGreyButton("Edit Credentials");
        editButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new android.content.Intent(SettingsActivity.this, CredentialsActivity.class));
            }
        });
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        editParams.topMargin = 8;
        panelGeneral.addView(editButton, editParams);

        panelGeneral.addView(createSectionLabel("Display"));
        allowSleepCheck = new CheckBox(this);
        allowSleepCheck.setText("Sleep between updates");
        allowSleepCheck.setTextColor(0xFF000000);
        allowSleepCheck.setChecked(ApiPrefs.isAllowSleep(this));
        panelGeneral.addView(allowSleepCheck);

        sleepHint = new TextView(this);
        sleepHint.setText("Set screensaver to TRMNL, sleep after 2 min");
        sleepHint.setTextSize(11);
        sleepHint.setTextColor(0xFF888888);
        sleepHint.setPadding(40, 0, 0, 0);
        sleepHint.setVisibility(allowSleepCheck.isChecked() ? View.VISIBLE : View.GONE);
        panelGeneral.addView(sleepHint);

        allowSleepCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sleepHint.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                flashRefresh();
            }
        });

        panelGeneral.addView(createSectionLabel("Gift Mode"));
        giftModeCheck = new CheckBox(this);
        giftModeCheck.setText("Enable gift mode");
        giftModeCheck.setTextColor(0xFF000000);
        giftModeCheck.setChecked(ApiPrefs.isGiftModeEnabled(this));
        giftModeCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateGiftSettingsVisibility();
                if (isChecked) {
                    startActivity(new android.content.Intent(SettingsActivity.this, GiftModeSettingsActivity.class));
                }
            }
        });
        panelGeneral.addView(giftModeCheck);

        giftSettingsButton = createGreyButton("Configure Gift Mode");
        giftSettingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new android.content.Intent(SettingsActivity.this, GiftModeSettingsActivity.class));
            }
        });
        LinearLayout.LayoutParams giftBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        giftBtnParams.topMargin = 6;
        panelGeneral.addView(giftSettingsButton, giftBtnParams);
        updateGiftSettingsVisibility();

        main.addView(panelGeneral);

        // ── Panel: Network ───────────────────────────────────────────────────
        final LinearLayout panelNetwork = new LinearLayout(this);
        panelNetwork.setOrientation(LinearLayout.VERTICAL);
        panelNetwork.setVisibility(View.GONE);

        panelNetwork.addView(createSectionLabel("WiFi"));
        wifiStatusView = new TextView(this);
        wifiStatusView.setTextSize(12);
        wifiStatusView.setTextColor(0xFF444444);
        LinearLayout.LayoutParams wifiStatusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wifiStatusParams.topMargin = 6;
        panelNetwork.addView(wifiStatusView, wifiStatusParams);
        wifiStatusView.setText(getWifiStatusText());

        Button wifiSettingsButton = createGreyButton("WiFi Settings");
        wifiSettingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
            }
        });
        LinearLayout.LayoutParams wifiSettBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wifiSettBtnParams.topMargin = 8;
        wifiSettBtnParams.bottomMargin = 12;
        panelNetwork.addView(wifiSettingsButton, wifiSettBtnParams);

        panelNetwork.addView(createSectionLabel("Options"));
        allowHttpCheck = new CheckBox(this);
        allowHttpCheck.setText("Allow HTTP (insecure)");
        allowHttpCheck.setTextColor(0xFF000000);
        allowHttpCheck.setChecked(ApiPrefs.isAllowHttp(this));
        panelNetwork.addView(allowHttpCheck);

        TextView httpHint = new TextView(this);
        httpHint.setText("Enable for local/BYOS servers without HTTPS");
        httpHint.setTextSize(11);
        httpHint.setTextColor(0xFF888888);
        httpHint.setPadding(40, 0, 0, 0);
        panelNetwork.addView(httpHint);

        allowSelfSignedCheck = new CheckBox(this);
        allowSelfSignedCheck.setText("Allow self-signed certificates");
        allowSelfSignedCheck.setTextColor(0xFF000000);
        allowSelfSignedCheck.setChecked(ApiPrefs.isAllowSelfSignedCerts(this));
        LinearLayout.LayoutParams selfSignedParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        selfSignedParams.topMargin = 8;
        panelNetwork.addView(allowSelfSignedCheck, selfSignedParams);

        TextView selfSignedHint = new TextView(this);
        selfSignedHint.setText("Trust HTTPS servers with self-signed certs");
        selfSignedHint.setTextSize(11);
        selfSignedHint.setTextColor(0xFF888888);
        selfSignedHint.setPadding(40, 0, 0, 0);
        panelNetwork.addView(selfSignedHint);

        autoDisableWifiCheck = new CheckBox(this);
        autoDisableWifiCheck.setText("Auto-disable WiFi");
        autoDisableWifiCheck.setTextColor(0xFF000000);
        autoDisableWifiCheck.setChecked(ApiPrefs.isAutoDisableWifi(this));
        LinearLayout.LayoutParams wifiParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wifiParams.topMargin = 8;
        panelNetwork.addView(autoDisableWifiCheck, wifiParams);

        TextView wifiHint = new TextView(this);
        wifiHint.setText("Turn off WiFi between fetches to save battery");
        wifiHint.setTextSize(11);
        wifiHint.setTextColor(0xFF888888);
        wifiHint.setPadding(40, 0, 0, 0);
        panelNetwork.addView(wifiHint);

        main.addView(panelNetwork);

        // ── Panel: System ────────────────────────────────────────────────────
        final LinearLayout panelSystem = new LinearLayout(this);
        panelSystem.setOrientation(LinearLayout.VERTICAL);
        panelSystem.setVisibility(View.GONE);

        panelSystem.addView(createSectionLabel("Debug Logs"));
        fileLoggingCheck = new CheckBox(this);
        fileLoggingCheck.setText("Save logs to file");
        fileLoggingCheck.setTextColor(0xFF000000);
        fileLoggingCheck.setChecked(ApiPrefs.isFileLoggingEnabled(this));
        panelSystem.addView(fileLoggingCheck);

        TextView logHint = new TextView(this);
        logHint.setText("/media/My Files/trmnl.log");
        logHint.setTextSize(11);
        logHint.setTextColor(0xFF888888);
        logHint.setPadding(40, 0, 0, 0);
        panelSystem.addView(logHint);

        Button clearLogsButton = createGreyButton("Clear Logs");
        clearLogsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { FileLogger.clear(); }
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clearParams.topMargin = 6;
        panelSystem.addView(clearLogsButton, clearParams);

        panelSystem.addView(createSectionLabel("Device"));
        LinearLayout deviceRow = new LinearLayout(this);
        deviceRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams deviceRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        deviceRowParams.topMargin = 6;
        deviceRowParams.bottomMargin = 80;

        Button deviceSettingsButton = createGreyButton("Nook Settings");
        deviceSettingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                android.content.Intent nookIntent = new android.content.Intent();
                nookIntent.setComponent(new android.content.ComponentName(
                        "com.home.nmyshkin.nooksettings",
                        "net.dinglisch.android.tasker.Kid"));
                nookIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(nookIntent);
            }
        });
        deviceRow.addView(deviceSettingsButton);

        Button appsDrawerButton = createGreyButton("Apps");
        LinearLayout.LayoutParams appsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        appsParams.leftMargin = 8;
        appsDrawerButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                android.content.Intent appsIntent = new android.content.Intent();
                appsIntent.setComponent(new android.content.ComponentName(
                        "JakedUp.AppDrawer",
                        "JakedUp.AppDrawer.Main"));
                appsIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(appsIntent);
            }
        });
        deviceRow.addView(appsDrawerButton, appsParams);
        panelSystem.addView(deviceRow, deviceRowParams);

        main.addView(panelSystem);

        // ── Tab switching logic ───────────────────────────────────────────────
        final LinearLayout[] panels = { panelGeneral, panelNetwork, panelSystem };
        final Button[] tabs = { tabGeneral, tabNetwork, tabSystem };

        View.OnClickListener tabClick = new View.OnClickListener() {
            public void onClick(View v) {
                for (int i = 0; i < tabs.length; i++) {
                    boolean active = tabs[i] == v;
                    panels[i].setVisibility(active ? View.VISIBLE : View.GONE);
                    tabs[i].setBackgroundColor(active ? 0xFF000000 : 0xFFDDDDDD);
                    tabs[i].setTextColor(active ? 0xFFFFFFFF : 0xFF444444);
                }
            }
        };
        tabGeneral.setOnClickListener(tabClick);
        tabNetwork.setOnClickListener(tabClick);
        tabSystem.setOnClickListener(tabClick);
        // start on General (already active styling)
        tabGeneral.setBackgroundColor(0xFF000000);
        tabGeneral.setTextColor(0xFFFFFFFF);

        scroll.addView(main);
        rootLayout.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));

        // Back button at bottom of screen
        Button backButton = new Button(this);
        backButton.setText("Back");
        backButton.setTextColor(0xFF000000);
        backButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveDisplayPrefs();
                finish();
            }
        });
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        backParams.bottomMargin = 16;
        rootLayout.addView(backButton, backParams);

        // No rotation - keep native orientation for keyboard compatibility
        outerRoot = new FrameLayout(this);
        outerRoot.addView(rootLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));

        flashOverlay = new View(this);
        flashOverlay.setBackgroundColor(0xFF000000);
        flashOverlay.setVisibility(View.GONE);
        outerRoot.addView(flashOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));

        setContentView(outerRoot);
    }

    private String getWifiStatusText() {
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wm == null || !wm.isWifiEnabled()) return "WiFi off";
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (ni != null && ni.isConnected()) {
                WifiInfo info = wm.getConnectionInfo();
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }
                return "Connected: " + (ssid != null ? ssid : "unknown");
            }
        }
        return "Not connected";
    }

    private TextView createSectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setTextColor(0xFF000000);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = 28;
        label.setLayoutParams(params);
        return label;
    }

    private Button createTabButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(0xFF444444);
        btn.setBackgroundColor(0xFFDDDDDD);
        return btn;
    }

    private Button createGreyButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(0xFF444444);
        btn.setBackgroundColor(0xFFDDDDDD);
        return btn;
    }

    private void updateGiftSettingsVisibility() {
        if (giftSettingsButton != null && giftModeCheck != null) {
            giftSettingsButton.setVisibility(giftModeCheck.isChecked() ? View.VISIBLE : View.GONE);
        }
    }

    private void flashRefresh() {
        if (flashOverlay == null || outerRoot == null) return;
        flashOverlay.setBackgroundColor(0xFF000000);
        flashOverlay.setVisibility(View.VISIBLE);
        outerRoot.bringChildToFront(flashOverlay);
        outerRoot.invalidate();
        handler.postDelayed(new Runnable() {
            public void run() {
                if (flashOverlay == null) return;
                flashOverlay.setBackgroundColor(0xFFFFFFFF);
                outerRoot.invalidate();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        if (flashOverlay != null) {
                            flashOverlay.setVisibility(View.GONE);
                        }
                        if (outerRoot != null) {
                            outerRoot.invalidate();
                        }
                    }
                }, 100);
            }
        }, 100);
    }

    protected void onResume() {
        super.onResume();
        if (statusView != null) {
            statusView.setText(ApiPrefs.hasCredentials(this) ? "Configured" : "Not set");
        }
        if (allowSleepCheck != null) allowSleepCheck.setChecked(ApiPrefs.isAllowSleep(this));
        if (fileLoggingCheck != null) fileLoggingCheck.setChecked(ApiPrefs.isFileLoggingEnabled(this));
        if (giftModeCheck != null) giftModeCheck.setChecked(ApiPrefs.isGiftModeEnabled(this));
        if (sleepHint != null) sleepHint.setVisibility(allowSleepCheck.isChecked() ? View.VISIBLE : View.GONE);
        if (giftSettingsButton != null && giftModeCheck != null) {
            giftSettingsButton.setVisibility(giftModeCheck.isChecked() ? View.VISIBLE : View.GONE);
        }
        if (allowHttpCheck != null) allowHttpCheck.setChecked(ApiPrefs.isAllowHttp(this));
        if (allowSelfSignedCheck != null) allowSelfSignedCheck.setChecked(ApiPrefs.isAllowSelfSignedCerts(this));
        if (autoDisableWifiCheck != null) autoDisableWifiCheck.setChecked(ApiPrefs.isAutoDisableWifi(this));
        if (wifiStatusView != null) wifiStatusView.setText(getWifiStatusText());
    }

    protected void onPause() {
        saveDisplayPrefs();
        super.onPause();
    }

    private void saveDisplayPrefs() {
        if (allowSleepCheck != null) ApiPrefs.setAllowSleep(this, allowSleepCheck.isChecked());
        if (fileLoggingCheck != null) {
            boolean enabled = fileLoggingCheck.isChecked();
            ApiPrefs.setFileLoggingEnabled(this, enabled);
            FileLogger.setEnabled(enabled);
        }
        if (giftModeCheck != null) ApiPrefs.setGiftModeEnabled(this, giftModeCheck.isChecked());
        if (allowHttpCheck != null) ApiPrefs.setAllowHttp(this, allowHttpCheck.isChecked());
        if (allowSelfSignedCheck != null) ApiPrefs.setAllowSelfSignedCerts(this, allowSelfSignedCheck.isChecked());
        if (autoDisableWifiCheck != null) ApiPrefs.setAutoDisableWifi(this, autoDisableWifiCheck.isChecked());
    }
}
