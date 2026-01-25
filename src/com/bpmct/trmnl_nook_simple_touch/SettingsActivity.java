package com.bpmct.trmnl_nook_simple_touch;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;

public class SettingsActivity extends Activity {
    private static final int APP_ROTATION_DEGREES = 90;
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

        TextView hint = new TextView(this);
        hint.setText("Tap anywhere to return");
        hint.setTextSize(14);
        hint.setTextColor(0xFF000000);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = 12;
        inner.addView(hint, hintParams);

        FrameLayout.LayoutParams rotateParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(inner, rotateParams);

        root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        RotateLayout rotateRoot = new RotateLayout(this);
        rotateRoot.setAngle(APP_ROTATION_DEGREES);
        rotateRoot.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));
        setContentView(rotateRoot);
    }
}
