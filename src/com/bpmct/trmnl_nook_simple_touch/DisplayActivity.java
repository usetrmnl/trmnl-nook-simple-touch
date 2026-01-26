package com.bpmct.trmnl_nook_simple_touch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.view.Gravity;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Hashtable;
import java.util.Locale;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;

import org.json.JSONObject;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
public class DisplayActivity extends Activity {
    public static final String EXTRA_CLEAR_IMAGE = "clear_image";
    private static final String TAG = "TRMNLAPI";
    private static final long DEFAULT_REFRESH_MS = 15 * 60 * 1000;
    private static final String API_DISPLAY_PATH = "/display";
    private TextView contentView;
    private TextView logView;
    private ImageView imageView;
    private ScrollView contentScroll;
    private RotateLayout appRotateLayout;
    private FrameLayout rootLayout;
    private LinearLayout menuLayout;
    private View menuScrim;
    private View flashOverlay;
    private TextView batteryView;
    private RotateLayout imageRotateLayout;
    private boolean menuVisible = false;
    private final Handler refreshHandler = new Handler();
    private Runnable refreshRunnable;
    private volatile boolean fetchInProgress = false;
    private volatile long refreshMs = DEFAULT_REFRESH_MS;
    private final StringBuilder logBuffer = new StringBuilder();
    private static final int MAX_LOG_CHARS = 6000;
    private static final int APP_ROTATION_DEGREES = 90;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // NOOK Simple Touch is API 7 (no nav bar); keep this deterministic.
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));
        rootLayout = root;

        // Simple layout: log panel + image or scrollable response panel
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        logView = new TextView(this);
        logView.setPadding(20, 20, 20, 20);
        logView.setTextColor(0xFF000000); // Black text for e-ink
        logView.setTextSize(12);
        logView.setText("Logs:\n");
        // Keep log panel reasonably small.
        contentLayout.addView(logView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                220));

        imageRotateLayout = new RotateLayout(this);
        imageRotateLayout.setAngle((360 - APP_ROTATION_DEGREES) % 360);
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        imageView.setVisibility(View.GONE);
        imageRotateLayout.addView(imageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));
        contentLayout.addView(imageRotateLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                0,
                1.0f));

        contentScroll = new ScrollView(this);
        contentView = new TextView(this);
        contentView.setPadding(20, 20, 20, 20);
        contentView.setTextColor(0xFF000000); // Black text for e-ink
        contentView.setTextSize(16);
        contentView.setText("Loading...");
        contentScroll.addView(contentView);
        contentLayout.addView(contentScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                0,
                1.0f));

        root.addView(contentLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        // Tap anywhere on content to toggle the menu.
        View.OnClickListener toggleListener = new View.OnClickListener() {
            public void onClick(View v) {
                toggleMenu();
            }
        };
        contentLayout.setOnClickListener(toggleListener);
        logView.setOnClickListener(toggleListener);
        contentScroll.setOnClickListener(toggleListener);
        contentView.setOnClickListener(toggleListener);
        imageView.setOnClickListener(toggleListener);

        // Scrim for closing menu when tapping outside.
        menuScrim = new View(this);
        menuScrim.setVisibility(View.GONE);
        menuScrim.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                hideMenu();
            }
        });
        root.addView(menuScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        // Flash overlay for e-ink ghosting reduction.
        flashOverlay = new View(this);
        flashOverlay.setBackgroundColor(0xFF000000);
        flashOverlay.setVisibility(View.GONE);
        root.addView(flashOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        // Menu content (normal orientation; root is rotated).
        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.HORIZONTAL);
        menuLayout.setVisibility(View.GONE);
        menuLayout.setClickable(true);
        menuLayout.setFocusable(true);

        menuLayout.setPadding(18, 12, 18, 12);
        menuLayout.setBackgroundColor(0xFFEFEFEF);

        batteryView = new TextView(this);
        batteryView.setTextColor(0xFF000000);
        batteryView.setTextSize(14);
        batteryView.setText("Battery: --%");
        menuLayout.addView(batteryView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button nextButton = new Button(this);
        nextButton.setText("Next");
        nextButton.setTextColor(0xFF000000);
        nextButton.setClickable(true);
        nextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                logD("menu: next tapped");
                hideMenu();
                startFetch();
            }
        });
        nextButton.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent event) {
                logD("menu: next touch action=" + event.getAction());
                return false;
            }
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nextParams.leftMargin = 18;
        menuLayout.addView(nextButton, nextParams);

        Button settingsButton = new Button(this);
        settingsButton.setText("Settings");
        settingsButton.setTextColor(0xFF000000);
        settingsButton.setClickable(true);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                logD("menu: settings tapped");
                hideMenu();
                try {
                    startActivity(new Intent(DisplayActivity.this, SettingsActivity.class));
                } catch (Throwable t) {
                    logW("settings launch failed: " + t);
                }
            }
        });
        settingsButton.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent event) {
                logD("menu: settings touch action=" + event.getAction());
                return false;
            }
        });
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        settingsParams.leftMargin = 18;
        menuLayout.addView(settingsButton, settingsParams);

        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(menuLayout, menuParams);
        menuLayout.bringToFront();

        appRotateLayout = new RotateLayout(this);
        appRotateLayout.setAngle(APP_ROTATION_DEGREES);
        appRotateLayout.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        setContentView(appRotateLayout);

        // Initial fetch (next display)
        if (ensureCredentials()) {
            startFetch();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply fullscreen flags in case system UI appeared
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        applyIntentState(getIntent());
        if (ensureCredentials()) {
            if (!fetchInProgress) {
                startFetch();
            }
            scheduleRefresh();
        }
    }

    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntentState(intent);
    }

    private void applyIntentState(Intent intent) {
        if (intent == null) return;
        if (intent.getBooleanExtra(EXTRA_CLEAR_IMAGE, false)) {
            if (imageView != null) imageView.setVisibility(View.GONE);
            if (contentScroll != null) contentScroll.setVisibility(View.VISIBLE);
            if (logView != null) logView.setVisibility(View.VISIBLE);
            intent.removeExtra(EXTRA_CLEAR_IMAGE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    /**
     * Reads battery voltage from ACTION_BATTERY_CHANGED sticky broadcast (API 7).
     * EXTRA_VOLTAGE is millivolts; returns volts (e.g. 3.6f), or -1f if unknown.
     */
    private static float getBatteryVoltage(Context context) {
        if (context == null) return -1f;
        try {
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null) return -1f;
            int mv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (mv <= 0) return -1f;
            return mv / 1000f;
        } catch (Throwable t) {
            return -1f;
        }
    }

    /** Battery percentage (0-100) from ACTION_BATTERY_CHANGED, or -1 if unknown. */
    private static int getBatteryPercent(Context context) {
        if (context == null) return -1;
        try {
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null) return -1;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return -1;
            return Math.round((level * 100f) / scale);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** WiFi RSSI in dBm (e.g. -69), or -999 if unknown. Requires ACCESS_WIFI_STATE. */
    private static int getWifiRssi(Context context) {
        if (context == null) return -999;
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return -999;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return -999;
            return info.getRssi();
        } catch (Throwable t) {
            return -999;
        }
    }

    private void startFetch() {
        if (!ensureCredentials()) {
            return;
        }
        if (fetchInProgress) {
            return;
        }
        fetchInProgress = true;
        String httpsUrl = ApiPrefs.getApiBaseUrl(this) + API_DISPLAY_PATH;
        logD("start: " + httpsUrl);
        ApiFetchTask.start(this, httpsUrl, ApiPrefs.getApiId(this), ApiPrefs.getApiToken(this));
    }

    private void scheduleRefresh() {
        if (refreshRunnable == null) {
            refreshRunnable = new Runnable() {
                public void run() {
                    startFetch();
                    refreshHandler.postDelayed(this, refreshMs);
                }
            };
        }
        refreshHandler.removeCallbacks(refreshRunnable);
        logD("next display in " + (refreshMs / 1000L) + "s");
        refreshHandler.postDelayed(refreshRunnable, refreshMs);
    }

    private void updateRefreshRateSeconds(final int seconds) {
        if (seconds <= 0) {
            return;
        }
        long newMs = seconds * 1000L;
        if (newMs == refreshMs) {
            return;
        }
        refreshMs = newMs;
        logD("refresh rate set to " + seconds + "s");
        refreshHandler.post(new Runnable() {
            public void run() {
                scheduleRefresh();
            }
        });
    }

    private void appendLogLine(String line) {
        // Keep buffer bounded.
        if (logBuffer.length() > MAX_LOG_CHARS) {
            logBuffer.delete(0, logBuffer.length() - MAX_LOG_CHARS);
        }
        logBuffer.append(line).append("\n");
        if (logView != null) {
            logView.setText("Logs:\n" + logBuffer.toString());
        }
    }

    private void toggleMenu() {
        if (menuVisible) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    private void showMenu() {
        menuVisible = true;
        updateMenuBattery();
        if (menuLayout != null) menuLayout.setVisibility(View.VISIBLE);
        if (menuScrim != null) menuScrim.setVisibility(View.VISIBLE);
    }

    private void hideMenu() {
        menuVisible = false;
        if (menuLayout != null) menuLayout.setVisibility(View.GONE);
        if (menuScrim != null) menuScrim.setVisibility(View.GONE);
        flashEinkTransition();
    }

    private void updateMenuBattery() {
        if (batteryView == null) return;
        int percent = getBatteryPercent(this);
        if (percent >= 0) {
            batteryView.setText("Battery: " + percent + "%");
        } else {
            batteryView.setText("Battery: --%");
        }
    }

    private void forceFullRefresh() {
        if (imageRotateLayout != null) {
            imageRotateLayout.requestLayout();
            imageRotateLayout.invalidate();
        }
        if (flashOverlay != null) {
            flashOverlay.invalidate();
        }
        View root = getWindow().getDecorView();
        if (root == null) return;
        root.invalidate();
        root.requestLayout();
        root.postDelayed(new Runnable() {
            public void run() {
                View r = getWindow().getDecorView();
                if (r != null) r.invalidate();
            }
        }, 40);
    }

    private void refreshContentAfterMenu() {
        if (imageView != null && imageView.getVisibility() == View.VISIBLE) {
            imageView.invalidate();
        }
        if (contentScroll != null && contentScroll.getVisibility() == View.VISIBLE) {
            contentScroll.invalidate();
        }
        if (logView != null && logView.getVisibility() == View.VISIBLE) {
            logView.invalidate();
        }
        forceFullRefresh();
        refreshHandler.postDelayed(new Runnable() {
            public void run() {
                forceFullRefresh();
            }
        }, 120);
    }

    private void flashEinkTransition() {
        if (flashOverlay == null) {
            forceFullRefresh();
            return;
        }
        flashOverlay.post(new Runnable() {
            public void run() {
                flashOverlay.setBackgroundColor(0xFF000000);
                flashOverlay.setVisibility(View.VISIBLE);
                if (rootLayout != null) {
                    rootLayout.bringChildToFront(flashOverlay);
                    rootLayout.requestLayout();
                }
                forceFullRefresh();
            }
        });
        refreshHandler.postDelayed(new Runnable() {
            public void run() {
                if (flashOverlay != null) {
                    flashOverlay.setBackgroundColor(0xFFFFFFFF);
                    flashOverlay.setVisibility(View.VISIBLE);
                }
                forceFullRefresh();
                refreshHandler.postDelayed(new Runnable() {
                    public void run() {
                        if (flashOverlay != null) {
                            flashOverlay.setVisibility(View.GONE);
                        }
                        refreshContentAfterMenu();
                    }
                }, 80);
            }
        }, 80);
    }

    private boolean ensureCredentials() {
        if (!ApiPrefs.hasCredentials(this)) {
            startActivity(new Intent(this, SettingsActivity.class));
            return false;
        }
        return true;
    }

    private void logD(final String msg) {
        Log.d(TAG, msg);
        // Also show on-screen (in case logcat filters hide it).
        runOnUiThread(new Runnable() {
            public void run() {
                appendLogLine("D " + msg);
            }
        });
    }

    private void logW(final String msg) {
        Log.w(TAG, msg);
        runOnUiThread(new Runnable() {
            public void run() {
                appendLogLine("W " + msg);
            }
        });
    }

    private void logE(final String msg, final Throwable t) {
        Log.e(TAG, msg, t);
        runOnUiThread(new Runnable() {
            public void run() {
                appendLogLine("E " + msg + (t != null ? (": " + t.toString()) : ""));
            }
        });
    }

    /**
     * Fetches JSON from API and displays as text.
     */
    private static class ApiFetchTask extends AsyncTask {
        private final WeakReference activityRef;
        private final String httpsUrl;
        private final String apiId;
        private final String apiToken;
        private ApiFetchTask(DisplayActivity activity, String httpsUrl, String apiId, String apiToken) {
            this.activityRef = new WeakReference(activity);
            this.httpsUrl = httpsUrl;
            this.apiId = apiId;
            this.apiToken = apiToken;
        }

        public static void start(DisplayActivity activity, String httpsUrl, String apiId, String apiToken) {
            if (activity == null || httpsUrl == null) return;
            try {
                new ApiFetchTask(activity, httpsUrl, apiId, apiToken).execute(new Object[] { httpsUrl });
            } catch (Throwable t) {
                activity.logE("fetch start failed", t);
            }
        }

        protected Object doInBackground(Object[] params) {
            String httpsUrl = (String) params[0];
            DisplayActivity a = (DisplayActivity) activityRef.get();
            float batteryVoltage = getBatteryVoltage(a != null ? a : null);
            int rssi = getWifiRssi(a != null ? a : null);
            if (a != null && batteryVoltage >= 0f) a.logD("Battery-Voltage: " + String.format(Locale.US, "%.1f", batteryVoltage));
            if (a != null && rssi != -999) a.logD("rssi: " + rssi);
            
            // Try BouncyCastle TLS first (supports TLS 1.2)
            if (BouncyCastleHttpClient.isAvailable()) {
                if (a != null) a.logD("trying BouncyCastle TLS 1.2");
                Hashtable headers = buildApiHeaders(apiId, apiToken, batteryVoltage, rssi);
                String bcResult = BouncyCastleHttpClient.getHttps(
                        a != null ? a.getApplicationContext() : null,
                        httpsUrl,
                        headers);
                if (bcResult != null && !bcResult.startsWith("Error:")) {
                    ApiResult parsed = null;
                    if (a != null) {
                        parsed = a.parseResponseAndMaybeFetchImage(bcResult);
                    }
                    if (parsed != null) {
                        return parsed;
                    }
                    return new ApiResult(bcResult);
                }
                if (a != null) a.logW("BouncyCastle TLS failed: " + bcResult);
                return bcResult;
            }

            String error = "Error: TLS 1.2 client unavailable (BouncyCastle required)";
            if (a != null) a.logW(error);
            return error;
        }
        
        private Object fetchUrl(String url, boolean isHttps, String apiId, String apiToken,
                                float batteryVoltage, int rssi) {
            HttpURLConnection conn = null;
            try {
                DisplayActivity a0 = (DisplayActivity) activityRef.get();
                if (a0 != null) a0.logD("fetching: " + url + (isHttps ? " (HTTPS)" : " (HTTP)"));
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent", "TRMNL-Nook/1.0 (Android 2.1)");
                conn.setRequestProperty("Accept", "application/json");
                
                // Add API authentication headers (matching curl format)
                if (apiId != null) {
                    conn.setRequestProperty("ID", apiId);
                }
                if (apiToken != null) {
                    conn.setRequestProperty("access-token", apiToken);
                }
                if (batteryVoltage >= 0f) {
                    conn.setRequestProperty("Battery-Voltage", String.format(Locale.US, "%.1f", batteryVoltage));
                }
                if (rssi != -999) {
                    conn.setRequestProperty("rssi", String.valueOf(rssi));
                }

                // Explicit connect for API 7
                try {
                    conn.connect();
                } catch (Throwable t) {
                    String errorMsg = "Error: " + t.getMessage();
                    DisplayActivity a4 = (DisplayActivity) activityRef.get();
                    if (a4 != null) a4.logE("connect() failed", t);
                    return errorMsg;
                }

                int code;
                try {
                    code = conn.getResponseCode();
                } catch (Throwable t) {
                    String errorMsg = "Error: " + t.getMessage();
                    DisplayActivity a5 = (DisplayActivity) activityRef.get();
                    if (a5 != null) a5.logE("getResponseCode() failed", t);
                    // Log full stack trace for SSL errors
                    if (t.getMessage() != null && t.getMessage().contains("SSL")) {
                        DisplayActivity a6 = (DisplayActivity) activityRef.get();
                        if (a6 != null) a6.logE("SSL error details", t);
                    }
                    return errorMsg;
                }
                
                DisplayActivity a7 = (DisplayActivity) activityRef.get();
                if (a7 != null) a7.logD("response code: " + code);
                
                if (code == -1) {
                    String errorMsg = "Error: Connection failed (code=-1)";
                    DisplayActivity a8 = (DisplayActivity) activityRef.get();
                    if (a8 != null) a8.logW(errorMsg);
                    return errorMsg;
                }

                if (code >= 200 && code < 300) {
                    InputStream is = conn.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        sb.append(new String(buf, 0, n, "UTF-8"));
                    }
                    is.close();
                    String json = sb.toString();
                DisplayActivity a9 = (DisplayActivity) activityRef.get();
                    if (a9 != null) a9.logD("got " + json.length() + " chars from " + (isHttps ? "HTTPS" : "HTTP"));
                    return json;
                } else {
                    return "Error: HTTP " + code;
                }
            } catch (Throwable t) {
                String errorMsg = "Error: " + t.getMessage();
                DisplayActivity a10 = (DisplayActivity) activityRef.get();
                if (a10 != null) a10.logE("fetch failed", t);
                // Log full stack trace for SSL errors
                if (t.getMessage() != null && t.getMessage().contains("SSL")) {
                    DisplayActivity a11 = (DisplayActivity) activityRef.get();
                    if (a11 != null) a11.logE("SSL error full stack trace", t);
                }
                return errorMsg;
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Throwable ignored) {}
                }
            }
        }

        protected void onPostExecute(Object result) {
            final DisplayActivity a = (DisplayActivity) activityRef.get();
            if (a == null || a.contentView == null) return;

            a.fetchInProgress = false;
            if (result instanceof ApiResult) {
                ApiResult ar = (ApiResult) result;
                if (ar.showImage && ar.bitmap != null) {
                    if (ar.rawText != null) {
                        a.logD("response body:\n" + ar.rawText);
                    }
                    a.imageView.setImageBitmap(ar.bitmap);
                    a.imageView.setVisibility(View.VISIBLE);
                    if (a.contentScroll != null) {
                        a.contentScroll.setVisibility(View.GONE);
                    }
                    if (a.logView != null) {
                        a.logView.setVisibility(View.GONE);
                    }
                    if (ar.imageUrl != null) {
                        a.logD("image url: " + ar.imageUrl);
                    }
                    a.forceFullRefresh();
                    a.logD("displayed image");
                    a.logD("next display in " + (a.refreshMs / 1000L) + "s");
                    float v = getBatteryVoltage(a);
                    if (v >= 0f) a.logD("Battery-Voltage: " + String.format(Locale.US, "%.1f", v));
                    int rssi = getWifiRssi(a);
                    if (rssi != -999) a.logD("rssi: " + rssi);
                    return;
                }

                String text = ar.rawText != null ? ar.rawText : "Error: null result";
                a.contentView.setText(text);
                if (a.contentScroll != null) {
                    a.contentScroll.setVisibility(View.VISIBLE);
                }
                if (a.imageView != null) {
                    a.imageView.setVisibility(View.GONE);
                }
                if (a.logView != null) {
                    a.logView.setVisibility(View.VISIBLE);
                }
                a.forceFullRefresh();
                a.logD("response body:\n" + text);
                a.logD("displayed response");
                a.logD("next display in " + (a.refreshMs / 1000L) + "s");
                float v = getBatteryVoltage(a);
                if (v >= 0f) a.logD("Battery-Voltage: " + String.format(Locale.US, "%.1f", v));
                int rssi = getWifiRssi(a);
                if (rssi != -999) a.logD("rssi: " + rssi);
                return;
            }

            String text = result != null ? result.toString() : "Error: null result";
            a.contentView.setText(text);
            a.logD("response body:\n" + text);
            a.logD("displayed response");
            a.logD("next display in " + (a.refreshMs / 1000L) + "s");
            float v = getBatteryVoltage(a);
            if (v >= 0f) a.logD("Battery-Voltage: " + String.format(Locale.US, "%.1f", v));
            int rssi = getWifiRssi(a);
            if (rssi != -999) a.logD("rssi: " + rssi);
        }
    }

    private static class ApiResult {
        final String rawText;
        final boolean showImage;
        final Bitmap bitmap;
        final String imageUrl;

        ApiResult(String rawText) {
            this.rawText = rawText;
            this.showImage = false;
            this.bitmap = null;
            this.imageUrl = null;
        }

        ApiResult(String rawText, String imageUrl, Bitmap bitmap) {
            this.rawText = rawText;
            this.showImage = true;
            this.bitmap = bitmap;
            this.imageUrl = imageUrl;
        }
    }

    private ApiResult parseResponseAndMaybeFetchImage(String jsonText) {
        try {
            JSONObject obj = new JSONObject(jsonText);
            int status = obj.optInt("status", -1);
            // API returns 0 for display
            if (status != 0 && status != 200) {
                return new ApiResult(jsonText);
            }
            logD("api status: " + status);

            int refreshRateSeconds = obj.optInt("refresh_rate", -1);
            if (refreshRateSeconds > 0) {
                updateRefreshRateSeconds(refreshRateSeconds);
            }

            String imageUrl = obj.optString("image_url", null);
            if (imageUrl == null || imageUrl.length() == 0) {
                return new ApiResult(jsonText);
            }
            logD("api image_url: " + imageUrl);

            // Log a decoded URL for readability, but use the encoded URL for fetch.
            try {
                String decoded = URLDecoder.decode(imageUrl, "UTF-8");
                logD("decoded image url: " + decoded);
            } catch (Throwable ignored) {
            }

            Hashtable headers = buildImageHeaders();
            byte[] imageBytes = BouncyCastleHttpClient.getHttpsBytes(
                    getApplicationContext(),
                    imageUrl,
                    headers);
            if (imageBytes == null || imageBytes.length == 0) {
                logW("image fetch failed for url: " + imageUrl);
                return new ApiResult(jsonText);
            }
            logD("image bytes: " + imageBytes.length);

            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap == null) {
                logW("image decode failed");
                return new ApiResult(jsonText);
            }
            if (imageUrl.endsWith("/empty_state.bmp")) {
                bitmap = rotate90(bitmap);
            }
            return new ApiResult(jsonText, imageUrl, bitmap);
        } catch (Throwable t) {
            logW("response parse failed: " + t);
            return new ApiResult(jsonText);
        }
    }

    private Bitmap rotate90(Bitmap src) {
        try {
            Matrix m = new Matrix();
            m.postRotate(90f);
            return Bitmap.createBitmap(
                    src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        } catch (Throwable t) {
            logW("image rotate failed: " + t);
            return src;
        }
    }

    private static Hashtable buildApiHeaders(String apiId, String apiToken, float batteryVoltage, int rssi) {
        Hashtable headers = new Hashtable();
        headers.put("User-Agent", "TRMNL-Nook/1.0 (Android 2.1)");
        headers.put("Accept", "application/json");
        if (apiId != null) {
            headers.put("ID", apiId);
        }
        if (apiToken != null) {
            headers.put("access-token", apiToken);
        }
        if (batteryVoltage >= 0f) {
            headers.put("Battery-Voltage", String.format(Locale.US, "%.1f", batteryVoltage));
        }
        if (rssi != -999) {
            headers.put("rssi", String.valueOf(rssi));
        }
        return headers;
    }


    private static Hashtable buildImageHeaders() {
        Hashtable headers = new Hashtable();
        headers.put("User-Agent", "TRMNL-Nook/1.0 (Android 2.1)");
        headers.put("Accept", "image/*");
        return headers;
    }
}
