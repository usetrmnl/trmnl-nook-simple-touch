package com.bpmct.trmnl_nook_simple_touch;

import android.app.Activity;
import android.os.Bundle;
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

public class SettingsActivity extends Activity {
    private static final int APP_ROTATION_DEGREES = 90;
    private TextView statusView;
    private CheckBox allowSleepCheck;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(24, 24, 24, 24);
        inner.setBackgroundColor(0xFFFFFFFF);

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(20);
        title.setTextColor(0xFF000000);
        inner.addView(title);

        TextView statusLabel = new TextView(this);
        statusLabel.setText("Credentials");
        statusLabel.setTextSize(14);
        statusLabel.setTextColor(0xFF000000);
        LinearLayout.LayoutParams statusLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLabelParams.topMargin = 16;
        inner.addView(statusLabel, statusLabelParams);

        statusView = new TextView(this);
        statusView.setTextSize(12);
        statusView.setTextColor(0xFF000000);
        statusView.setText(ApiPrefs.hasCredentials(this) ? "Saved" : "Missing");
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = 6;
        inner.addView(statusView, statusParams);

        TextView displayLabel = new TextView(this);
        displayLabel.setText("Display / power");
        displayLabel.setTextSize(14);
        displayLabel.setTextColor(0xFF000000);
        LinearLayout.LayoutParams displayLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        displayLabelParams.topMargin = 20;
        inner.addView(displayLabel, displayLabelParams);

        allowSleepCheck = new CheckBox(this);
        allowSleepCheck.setText("Allow device to sleep between updates");
        allowSleepCheck.setTextColor(0xFF000000);
        allowSleepCheck.setChecked(ApiPrefs.isAllowSleep(this));
        inner.addView(allowSleepCheck, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = 16;
        inner.addView(actions, actionsParams);

        Button editButton = new Button(this);
        editButton.setText("Edit");
        editButton.setTextColor(0xFF000000);
        actions.addView(editButton);

        Button backButton = new Button(this);
        backButton.setText("Back");
        backButton.setTextColor(0xFF000000);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        backParams.leftMargin = 16;
        actions.addView(backButton, backParams);

        editButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new android.content.Intent(SettingsActivity.this, CredentialsActivity.class));
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveDisplayPrefs();
                finish();
            }
        });

        FrameLayout.LayoutParams rotateParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(inner, rotateParams);

        RotateLayout rotateRoot = new RotateLayout(this);
        rotateRoot.setAngle(APP_ROTATION_DEGREES);
        rotateRoot.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));
        setContentView(rotateRoot);
    }

    protected void onResume() {
        super.onResume();
        if (statusView != null) {
            statusView.setText(ApiPrefs.hasCredentials(this) ? "Saved" : "Missing");
        }
        if (allowSleepCheck != null) allowSleepCheck.setChecked(ApiPrefs.isAllowSleep(this));
    }

    protected void onPause() {
        saveDisplayPrefs();
        super.onPause();
    }

    private void saveDisplayPrefs() {
        if (allowSleepCheck != null) ApiPrefs.setAllowSleep(this, allowSleepCheck.isChecked());
    }
}
