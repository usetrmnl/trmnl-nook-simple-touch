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

        // Credentials
        main.addView(createSectionLabel("Credentials"));
        statusView = new TextView(this);
        statusView.setTextSize(12);
        statusView.setTextColor(0xFF444444);
        statusView.setText(ApiPrefs.hasCredentials(this) ? "Configured" : "Not set - find in Device Settings → Developer Perks on trmnl.com");
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = 6;
        main.addView(statusView, statusParams);
        Button editButton = createGreyButton("Edit Credentials");
        editButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new android.content.Intent(SettingsActivity.this, CredentialsActivity.class));
            }
        });
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        editParams.topMargin = 8;
        main.addView(editButton, editParams);

        // Display
        main.addView(createSectionLabel("Display"));
        allowSleepCheck = new CheckBox(this);
        allowSleepCheck.setText("Sleep between updates");
        allowSleepCheck.setTextColor(0xFF000000);
        allowSleepCheck.setChecked(ApiPrefs.isAllowSleep(this));
        main.addView(allowSleepCheck);

        sleepHint = new TextView(this);
        sleepHint.setText("Set screensaver to TRMNL, sleep after 2 min");
        sleepHint.setTextSize(11);
        sleepHint.setTextColor(0xFF888888);
        sleepHint.setPadding(40, 0, 0, 0);
        sleepHint.setVisibility(allowSleepCheck.isChecked() ? View.VISIBLE : View.GONE);
        main.addView(sleepHint);

        allowSleepCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sleepHint.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                flashRefresh();
            }
        });

        // Gift Mode
        main.addView(createSectionLabel("Gift Mode"));
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
        main.addView(giftModeCheck);

        giftSettingsButton = createGreyButton("Configure Gift Mode");
        giftSettingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new android.content.Intent(SettingsActivity.this, GiftModeSettingsActivity.class));
            }
        });
        LinearLayout.LayoutParams giftBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        giftBtnParams.topMargin = 6;
        main.addView(giftSettingsButton, giftBtnParams);
        updateGiftSettingsVisibility();

        // Network (for self-hosted/BYOS setups)
        main.addView(createSectionLabel("Network"));
        allowHttpCheck = new CheckBox(this);
        allowHttpCheck.setText("Allow HTTP (insecure)");
        allowHttpCheck.setTextColor(0xFF000000);
        allowHttpCheck.setChecked(ApiPrefs.isAllowHttp(this));
        main.addView(allowHttpCheck);

        TextView httpHint = new TextView(this);
        httpHint.setText("Enable for local/BYOS servers without HTTPS");
        httpHint.setTextSize(11);
        httpHint.setTextColor(0xFF888888);
        httpHint.setPadding(40, 0, 0, 0);
        main.addView(httpHint);

        allowSelfSignedCheck = new CheckBox(this);
        allowSelfSignedCheck.setText("Allow self-signed certificates");
        allowSelfSignedCheck.setTextColor(0xFF000000);
        allowSelfSignedCheck.setChecked(ApiPrefs.isAllowSelfSignedCerts(this));
        LinearLayout.LayoutParams selfSignedParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        selfSignedParams.topMargin = 8;
        main.addView(allowSelfSignedCheck, selfSignedParams);

        TextView selfSignedHint = new TextView(this);
        selfSignedHint.setText("Trust HTTPS servers with self-signed certs");
        selfSignedHint.setTextSize(11);
        selfSignedHint.setTextColor(0xFF888888);
        selfSignedHint.setPadding(40, 0, 0, 0);
        main.addView(selfSignedHint);

        autoDisableWifiCheck = new CheckBox(this);
        autoDisableWifiCheck.setText("Auto-disable WiFi");
        autoDisableWifiCheck.setTextColor(0xFF000000);
        autoDisableWifiCheck.setChecked(ApiPrefs.isAutoDisableWifi(this));
        LinearLayout.LayoutParams wifiParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wifiParams.topMargin = 8;
        main.addView(autoDisableWifiCheck, wifiParams);

        TextView wifiHint = new TextView(this);
        wifiHint.setText("Turn off WiFi between fetches to save battery");
        wifiHint.setTextSize(11);
        wifiHint.setTextColor(0xFF888888);
        wifiHint.setPadding(40, 0, 0, 0);
        main.addView(wifiHint);

        // Debug Logs
        main.addView(createSectionLabel("Debug Logs"));
        fileLoggingCheck = new CheckBox(this);
        fileLoggingCheck.setText("Save to file");
        fileLoggingCheck.setTextColor(0xFF000000);
        fileLoggingCheck.setChecked(ApiPrefs.isFileLoggingEnabled(this));
        main.addView(fileLoggingCheck);

        TextView logHint = new TextView(this);
        logHint.setText("/media/My Files/trmnl.log");
        logHint.setTextSize(11);
        logHint.setTextColor(0xFF888888);
        logHint.setPadding(40, 0, 0, 0);
        main.addView(logHint);

        Button clearLogsButton = createGreyButton("Clear Logs");
        clearLogsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FileLogger.clear();
            }
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clearParams.topMargin = 6;
        main.addView(clearLogsButton, clearParams);

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
            statusView.setText(ApiPrefs.hasCredentials(this) ? "Configured" : "Not set - find in Device Settings → Developer Perks on trmnl.com");
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
