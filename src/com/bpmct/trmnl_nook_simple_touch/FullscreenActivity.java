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

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;

import org.json.JSONObject;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
public class FullscreenActivity extends Activity {
    private static final String TAG = "TRMNLAPI";
    private static final long DEFAULT_REFRESH_MS = 15 * 60 * 1000;
    private TextView contentView;
    private TextView logView;
    private ImageView imageView;
    private ScrollView contentScroll;
    private final Handler refreshHandler = new Handler();
    private Runnable refreshRunnable;
    private volatile boolean fetchInProgress = false;
    private volatile long refreshMs = DEFAULT_REFRESH_MS;
    private final StringBuilder logBuffer = new StringBuilder();
    private static final int MAX_LOG_CHARS = 6000;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // NOOK Simple Touch is API 7 (no nav bar); keep this deterministic.
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Simple layout: log panel + image or scrollable response panel
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        logView = new TextView(this);
        logView.setPadding(20, 20, 20, 20);
        logView.setTextColor(0xFF000000); // Black text for e-ink
        logView.setTextSize(12);
        logView.setText("Logs:\n");
        // Keep log panel reasonably small.
        root.addView(logView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                220));

        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        imageView.setVisibility(View.GONE);
        root.addView(imageView, new LinearLayout.LayoutParams(
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
        root.addView(contentScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                0,
                1.0f));

        setContentView(root);

        // Initial fetch (next display)
        startFetch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply fullscreen flags in case system UI appeared
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        scheduleRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    /**
     * Reads battery level from ACTION_BATTERY_CHANGED sticky broadcast (API 7).
     * Returns 0–100, or -1 if unknown.
     */
    private static int getBatteryLevelPercent(Context context) {
        if (context == null) return -1;
        try {
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null) return -1;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
            if (scale <= 0 || level < 0) return -1;
            return (level * 100) / scale;
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
        if (fetchInProgress) {
            return;
        }
        fetchInProgress = true;
        String httpsUrl = ApiConfig.API_BASE_URL + ApiConfig.API_DISPLAY_PATH;
        logD("start: " + httpsUrl);
        ApiFetchTask.start(this, httpsUrl, ApiConfig.API_ID, ApiConfig.API_TOKEN);
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

        private ApiFetchTask(FullscreenActivity activity, String httpsUrl, String apiId, String apiToken) {
            this.activityRef = new WeakReference(activity);
            this.httpsUrl = httpsUrl;
            this.apiId = apiId;
            this.apiToken = apiToken;
        }

        public static void start(FullscreenActivity activity, String httpsUrl, String apiId, String apiToken) {
            if (activity == null || httpsUrl == null) return;
            try {
                new ApiFetchTask(activity, httpsUrl, apiId, apiToken).execute(new Object[] { httpsUrl });
            } catch (Throwable t) {
                activity.logE("fetch start failed", t);
            }
        }

        protected Object doInBackground(Object[] params) {
            String httpsUrl = (String) params[0];
            FullscreenActivity a = (FullscreenActivity) activityRef.get();
            int batteryLevel = getBatteryLevelPercent(a != null ? a : null);
            int rssi = getWifiRssi(a != null ? a : null);
            if (a != null && batteryLevel >= 0) a.logD("battery-level: " + batteryLevel);
            if (a != null && rssi != -999) a.logD("rssi: " + rssi);
            
            // Try BouncyCastle TLS first (supports TLS 1.2)
            if (BouncyCastleHttpClient.isAvailable()) {
                if (a != null) a.logD("trying BouncyCastle TLS 1.2");
                String bcResult = BouncyCastleHttpClient.getHttps(
                        a != null ? a.getApplicationContext() : null,
                        httpsUrl,
                        apiId,
                        apiToken,
                        batteryLevel,
                        rssi);
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
            }
            
            // Fallback to system HttpURLConnection (TLS 1.0 only)
            Object result = fetchUrl(httpsUrl, true, apiId, apiToken, batteryLevel, rssi);
            if (result != null && !result.toString().startsWith("Error:")) {
                ApiResult parsed = null;
                if (a != null) {
                    parsed = a.parseResponseAndMaybeFetchImage(result.toString());
                }
                if (parsed != null) {
                    return parsed;
                }
                return new ApiResult(result.toString());
            }
            
            return result;
        }
        
        private Object fetchUrl(String url, boolean isHttps, String apiId, String apiToken, int batteryLevel, int rssi) {
            HttpURLConnection conn = null;
            try {
                FullscreenActivity a0 = (FullscreenActivity) activityRef.get();
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
                if (batteryLevel >= 0) {
                    conn.setRequestProperty("battery-level", String.valueOf(batteryLevel));
                    conn.setRequestProperty("Battery-Voltage", String.valueOf(batteryLevel));
                }
                if (rssi != -999) {
                    conn.setRequestProperty("rssi", String.valueOf(rssi));
                }

                // Explicit connect for API 7
                try {
                    conn.connect();
                } catch (Throwable t) {
                    String errorMsg = "Error: " + t.getMessage();
                    FullscreenActivity a4 = (FullscreenActivity) activityRef.get();
                    if (a4 != null) a4.logE("connect() failed", t);
                    return errorMsg;
                }

                int code;
                try {
                    code = conn.getResponseCode();
                } catch (Throwable t) {
                    String errorMsg = "Error: " + t.getMessage();
                    FullscreenActivity a5 = (FullscreenActivity) activityRef.get();
                    if (a5 != null) a5.logE("getResponseCode() failed", t);
                    // Log full stack trace for SSL errors
                    if (t.getMessage() != null && t.getMessage().contains("SSL")) {
                        FullscreenActivity a6 = (FullscreenActivity) activityRef.get();
                        if (a6 != null) a6.logE("SSL error details", t);
                    }
                    return errorMsg;
                }
                
                FullscreenActivity a7 = (FullscreenActivity) activityRef.get();
                if (a7 != null) a7.logD("response code: " + code);
                
                if (code == -1) {
                    String errorMsg = "Error: Connection failed (code=-1)";
                    FullscreenActivity a8 = (FullscreenActivity) activityRef.get();
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
                    FullscreenActivity a9 = (FullscreenActivity) activityRef.get();
                    if (a9 != null) a9.logD("got " + json.length() + " chars from " + (isHttps ? "HTTPS" : "HTTP"));
                    return json;
                } else {
                    return "Error: HTTP " + code;
                }
            } catch (Throwable t) {
                String errorMsg = "Error: " + t.getMessage();
                FullscreenActivity a10 = (FullscreenActivity) activityRef.get();
                if (a10 != null) a10.logE("fetch failed", t);
                // Log full stack trace for SSL errors
                if (t.getMessage() != null && t.getMessage().contains("SSL")) {
                    FullscreenActivity a11 = (FullscreenActivity) activityRef.get();
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
            final FullscreenActivity a = (FullscreenActivity) activityRef.get();
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
                    a.logD("displayed image");
                    a.logD("next display in " + (a.refreshMs / 1000L) + "s");
                    int bat = getBatteryLevelPercent(a);
                    if (bat >= 0) a.logD("battery-level: " + bat);
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
                a.logD("response body:\n" + text);
                a.logD("displayed response");
                a.logD("next display in " + (a.refreshMs / 1000L) + "s");
                int bat = getBatteryLevelPercent(a);
                if (bat >= 0) a.logD("battery-level: " + bat);
                int rssi = getWifiRssi(a);
                if (rssi != -999) a.logD("rssi: " + rssi);
                return;
            }

            String text = result != null ? result.toString() : "Error: null result";
            a.contentView.setText(text);
            a.logD("response body:\n" + text);
            a.logD("displayed response");
            a.logD("next display in " + (a.refreshMs / 1000L) + "s");
            int bat = getBatteryLevelPercent(a);
            if (bat >= 0) a.logD("battery-level: " + bat);
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
            if (status != 0) {
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

            byte[] imageBytes = BouncyCastleHttpClient.getHttpsBytes(
                    getApplicationContext(),
                    imageUrl);
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
}
