package com.bpmct.trmnl_nook_simple_touch;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CredentialsActivity extends Activity {
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
        title.setText("Edit Credentials");
        title.setTextSize(20);
        title.setTextColor(0xFF000000);
        inner.addView(title);

        TextView idLabel = new TextView(this);
        idLabel.setText("Device ID (MAC Address)");
        idLabel.setTextSize(14);
        idLabel.setTextColor(0xFF000000);
        LinearLayout.LayoutParams idLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        idLabelParams.topMargin = 16;
        inner.addView(idLabel, idLabelParams);

        final EditText idInput = new EditText(this);
        idInput.setSingleLine(true);
        idInput.setTextColor(0xFF000000);
        idInput.setTextSize(18);
        idInput.setPadding(12, 12, 12, 12);
        idInput.setHint("e.g. 12345:BYOD:1:B678");
        String existingId = ApiPrefs.getApiId(this);
        if (existingId != null) idInput.setText(existingId);
        LinearLayout.LayoutParams idParams = new LinearLayout.LayoutParams(
                420,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        inner.addView(idInput, idParams);

        TextView tokenLabel = new TextView(this);
        tokenLabel.setText("API Key");
        tokenLabel.setTextSize(14);
        tokenLabel.setTextColor(0xFF000000);
        LinearLayout.LayoutParams tokenLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tokenLabelParams.topMargin = 12;
        inner.addView(tokenLabel, tokenLabelParams);

        final EditText tokenInput = new EditText(this);
        tokenInput.setSingleLine(true);
        tokenInput.setTextColor(0xFF000000);
        tokenInput.setTextSize(18);
        tokenInput.setPadding(12, 12, 12, 12);
        tokenInput.setHint("access-token");
        String existingToken = ApiPrefs.getApiToken(this);
        if (existingToken != null) tokenInput.setText(existingToken);
        LinearLayout.LayoutParams tokenParams = new LinearLayout.LayoutParams(
                420,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        inner.addView(tokenInput, tokenParams);

        final TextView statusView = new TextView(this);
        statusView.setTextSize(12);
        statusView.setTextColor(0xFF000000);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = 8;
        inner.addView(statusView, statusParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = 16;
        inner.addView(actions, actionsParams);

        Button saveButton = new Button(this);
        saveButton.setText("Save");
        saveButton.setTextColor(0xFF000000);
        actions.addView(saveButton);

        Button clearButton = new Button(this);
        clearButton.setText("Clear");
        clearButton.setTextColor(0xFF000000);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clearParams.leftMargin = 16;
        actions.addView(clearButton, clearParams);

        Button backButton = new Button(this);
        backButton.setText("Back");
        backButton.setTextColor(0xFF000000);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        backParams.leftMargin = 16;
        actions.addView(backButton, backParams);

        saveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String id = idInput.getText() != null ? idInput.getText().toString().trim() : "";
                String token = tokenInput.getText() != null ? tokenInput.getText().toString().trim() : "";
                if (id.length() == 0 || token.length() == 0) {
                    statusView.setText("Device ID and API Key are required.");
                    return;
                }
                ApiPrefs.saveCredentials(CredentialsActivity.this, id, token);
                statusView.setText("Saved.");
                finish();
            }
        });

        clearButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                idInput.setText("");
                tokenInput.setText("");
                statusView.setText("");
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        FrameLayout.LayoutParams innerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        innerParams.topMargin = -120;
        root.addView(inner, innerParams);

        setContentView(root);
    }
}
