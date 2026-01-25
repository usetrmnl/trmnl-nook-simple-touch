package com.bpmct.trmnl_nook_simple_touch;

import android.app.Activity;
import android.os.Bundle;
import android.os.AsyncTask;
import android.util.Log;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.ViewGroup;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public class FullscreenActivity extends Activity {
    private static final String TAG = "TRMNLAPI";
    private TextView contentView;
    private TextView logView;
    private final StringBuilder logBuffer = new StringBuilder();
    private static final int MAX_LOG_CHARS = 6000;
    private static SSLSocketFactory trustAllSocketFactory = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // NOOK Simple Touch is API 7 (no nav bar); keep this deterministic.
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Simple layout: log panel + scrollable response panel
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

        ScrollView scrollView = new ScrollView(this);
        contentView = new TextView(this);
        contentView.setPadding(20, 20, 20, 20);
        contentView.setTextColor(0xFF000000); // Black text for e-ink
        contentView.setTextSize(16);
        contentView.setText("Loading...");
        scrollView.addView(contentView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        setContentView(root);

        // Fetch API (try HTTPS first, fallback to HTTP if SSL fails)
        String httpsUrl = ApiConfig.API_BASE_URL + ApiConfig.API_DISPLAY_PATH;
        String httpUrl = httpsUrl.replace("https://", "http://");
        logD("start: " + httpsUrl);
        ApiFetchTask.start(this, httpsUrl, httpUrl, ApiConfig.API_ID, ApiConfig.API_TOKEN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply fullscreen flags in case system UI appeared
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        private final String httpUrl;
        private final String apiId;
        private final String apiToken;

        private ApiFetchTask(FullscreenActivity activity, String httpsUrl, String httpUrl, String apiId, String apiToken) {
            this.activityRef = new WeakReference(activity);
            this.httpsUrl = httpsUrl;
            this.httpUrl = httpUrl;
            this.apiId = apiId;
            this.apiToken = apiToken;
        }

        public static void start(FullscreenActivity activity, String httpsUrl, String httpUrl, String apiId, String apiToken) {
            if (activity == null || httpsUrl == null) return;
            try {
                new ApiFetchTask(activity, httpsUrl, httpUrl, apiId, apiToken).execute(new Object[] { httpsUrl, httpUrl });
            } catch (Throwable t) {
                activity.logE("fetch start failed", t);
            }
        }

        protected Object doInBackground(Object[] params) {
            String httpsUrl = (String) params[0];
            String httpUrl = params.length > 1 ? (String) params[1] : null;
            
            // Try BouncyCastle TLS first (supports TLS 1.2)
            if (BouncyCastleHttpClient.isAvailable()) {
                FullscreenActivity a = (FullscreenActivity) activityRef.get();
                if (a != null) a.logD("trying BouncyCastle TLS 1.2");
                String bcResult = BouncyCastleHttpClient.getHttps(
                        a != null ? a.getApplicationContext() : null,
                        httpsUrl,
                        apiId,
                        apiToken);
                if (bcResult != null && !bcResult.startsWith("Error:")) {
                    return bcResult;
                }
                if (a != null) a.logW("BouncyCastle TLS failed: " + bcResult);
            }
            
            // Fallback to system HttpURLConnection (TLS 1.0 only)
            Object result = fetchUrl(httpsUrl, true, apiId, apiToken);
            
            // If HTTPS fails with SSL error, try HTTP fallback
            if (result != null && result.toString().contains("SSL") && httpUrl != null) {
                FullscreenActivity a = (FullscreenActivity) activityRef.get();
                if (a != null) a.logW("HTTPS failed with SSL error, trying HTTP fallback");
                Object httpResult = fetchUrl(httpUrl, false, apiId, apiToken);

                // If the server rejects plain HTTP, give a clearer, actionable message.
                if (httpResult != null && httpResult.toString().startsWith("Error: HTTP ")) {
                    String msg =
                            "HTTPS failed due to TLS/SSL handshake.\n\n" +
                            "System TLS (TLS 1.0 only) failed. BouncyCastle TLS 1.2 " +
                            (BouncyCastleHttpClient.isAvailable() ? "also failed" : "not available (see libs/README_SPONGYCASTLE.md)") + ".\n\n" +
                            "HTTP fallback also failed (" + httpResult + ").\n\n" +
                            "Fix: run a local HTTP→HTTPS proxy on your LAN and set ApiConfig.API_BASE_URL to that proxy (http://<LAN-IP>:<PORT>).";
                    return msg;
                }

                result = httpResult;
            }
            
            return result;
        }
        
        private Object fetchUrl(String url, boolean isHttps, String apiId, String apiToken) {
            HttpURLConnection conn = null;
            try {
                FullscreenActivity a0 = (FullscreenActivity) activityRef.get();
                if (a0 != null) a0.logD("fetching: " + url + (isHttps ? " (HTTPS)" : " (HTTP)"));
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                
                // Use custom SSL socket factory and hostname verifier for API 7 compatibility (HTTPS only)
                if (isHttps && conn instanceof HttpsURLConnection) {
                    HttpsURLConnection https = (HttpsURLConnection) conn;
                    SSLSocketFactory factory = getTrustAllSocketFactory();
                    if (factory != null) {
                        FullscreenActivity a1 = (FullscreenActivity) activityRef.get();
                        if (a1 != null) a1.logD("setting custom SSL socket factory");
                        https.setSSLSocketFactory(factory);
                    } else {
                        FullscreenActivity a2 = (FullscreenActivity) activityRef.get();
                        if (a2 != null) a2.logW("custom SSL socket factory is null, using default");
                    }
                    // Also bypass hostname verification (for testing only)
                    https.setHostnameVerifier(getTrustAllHostnameVerifier());
                    FullscreenActivity a3 = (FullscreenActivity) activityRef.get();
                    if (a3 != null) a3.logD("setting trust-all hostname verifier");
                }
                
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
            
            String text = result != null ? result.toString() : "Error: null result";
            a.contentView.setText(text);
            a.logD("displayed response");
        }
    }

    /**
     * Creates an SSLSocketFactory that accepts all certificates (testing only).
     * On API 7, we need to use "TLS" or "SSL" protocol, and handle old cipher suites.
     */
    private static synchronized SSLSocketFactory getTrustAllSocketFactory() {
        if (trustAllSocketFactory != null) {
            return trustAllSocketFactory;
        }
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }
                }
            };
            
            // Try TLS first, fallback to SSL for API 7
            SSLContext sc = null;
            try {
                sc = SSLContext.getInstance("TLS");
                Log.d(TAG, "using TLS protocol");
            } catch (Throwable t) {
                try {
                    sc = SSLContext.getInstance("SSL");
                    Log.d(TAG, "using SSL protocol (fallback)");
                } catch (Throwable t2) {
                    Log.e(TAG, "failed to get SSLContext: " + t2);
                    return null;
                }
            }
            
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            trustAllSocketFactory = sc.getSocketFactory();
            Log.d(TAG, "created trust-all socket factory");
            return trustAllSocketFactory;
        } catch (Throwable t) {
            Log.e(TAG, "failed to create trust-all socket factory: " + t, t);
            return null;
        }
    }

    /**
     * Creates a HostnameVerifier that accepts all hostnames (testing only).
     * This bypasses the "Hostname was not verified" error on API 7.
     */
    private static HostnameVerifier getTrustAllHostnameVerifier() {
        return new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                Log.d(TAG, "hostname verification: accepting " + hostname);
                return true; // Accept all hostnames
            }
        };
    }
}
