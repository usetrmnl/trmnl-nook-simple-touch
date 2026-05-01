package com.bpmct.trmnl_nook_simple_touch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Showcase Mode — 2×2 grid of cached TRMNL recipe screens.
 *
 * Grid loads instantly from disk cache. WiFi and fetching only happen
 * when the user taps into an individual cell (handled by DisplayActivity).
 */
public class ShowcaseActivity extends Activity {

    private static final String TAG = "ShowcaseMODE";

    // ---------------------------------------------------------------------------
    // Device credentials are stored in ApiPrefs and loaded at runtime.
    // Use getCellId(context, i) / getCellToken(context, i) — never cache these as fields.
    // ---------------------------------------------------------------------------
    static String getCellId(Context context, int cellIdx) {
        return ApiPrefs.getShowcaseCellId(context, cellIdx);
    }
    static String getCellToken(Context context, int cellIdx) {
        return ApiPrefs.getShowcaseCellToken(context, cellIdx);
    }
    static String getCellApiUrl(Context context, int cellIdx) {
        String url = ApiPrefs.getShowcaseCellUrl(context, cellIdx);
        if (url == null || url.length() == 0) return "https://usetrmnl.com/api/display";
        // Normalise: strip trailing slash, ensure /display suffix
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (!url.endsWith("/display")) url = url + "/display";
        return url;
    }
    static boolean isCellConfigured(Context context, int cellIdx) {
        // Token is the only required credential — device ID and URL are optional
        String token = getCellToken(context, cellIdx);
        return token != null && token.length() > 0;
    }

    /**
     * Returns true if only cell 0 is configured and cells 1–3 are all unconfigured.
     * In this case the app should skip the 2×2 grid and go straight to cell 0 fullscreen.
     */
    static boolean isOnlyCell0Configured(Context context) {
        if (!isCellConfigured(context, 0)) return false;
        for (int i = 1; i < NUM_CELLS; i++) {
            if (isCellConfigured(context, i)) return false;
        }
        return true;
    }

    private static final int APP_ROTATION_DEGREES = 90;
    static final int NUM_CELLS = 4;
    private static final int CELL_PADDING = 6;
    private static final int GRID_PADDING = 12;
    private static final int GRID_GAP = 10;

    // ---------------------------------------------------------------------------
    // Cache
    // ---------------------------------------------------------------------------
    private static final String CACHE_PREFIX = "showcase_cache_";

    /** Load a single cell bitmap from persistent cache, or null if not cached. */
    static Bitmap loadCachedBitmap(Context context, int cellIdx) {
        java.io.File f = new java.io.File(context.getFilesDir(), CACHE_PREFIX + cellIdx + ".png");
        if (!f.exists()) return null;
        try {
            return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Throwable t) {
            android.util.Log.w(TAG, "cache load cell " + cellIdx + ": " + t);
            return null;
        }
    }

    /** Write a cell bitmap to persistent cache. Called by DisplayActivity after a successful fetch. */
    static void saveCachedBitmap(Context context, int cellIdx, Bitmap bmp) {
        try {
            java.io.File f = new java.io.File(context.getFilesDir(), CACHE_PREFIX + cellIdx + ".png");
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            android.util.Log.d(TAG, "saved cache for cell " + cellIdx);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "cache save cell " + cellIdx + ": " + t);
        }
    }

    /** Delete all cached cell bitmaps. */
    static void clearCache(Context context) {
        for (int i = 0; i < NUM_CELLS; i++) {
            new java.io.File(context.getFilesDir(), CACHE_PREFIX + i + ".png").delete();
        }
        android.util.Log.d(TAG, "showcase cache cleared");
    }

    // ---------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------
    private Bitmap[] cellBitmaps = new Bitmap[NUM_CELLS];
    private ImageView[] cellImageViews = new ImageView[NUM_CELLS];
    private TextView[] cellStatusViews = new TextView[NUM_CELLS];

    private LinearLayout contentRoot;
    private RotateLayout appRotateLayout;

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        FrameLayout root = buildUI();

        appRotateLayout = new RotateLayout(this);
        appRotateLayout.setAngle(APP_ROTATION_DEGREES);
        appRotateLayout.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        setContentView(appRotateLayout);

        // If only cell 0 is configured, skip the grid and go fullscreen immediately
        if (isOnlyCell0Configured(this)) {
            Intent i = new Intent(this, DisplayActivity.class);
            i.putExtra(DisplayActivity.EXTRA_SHOWCASE_API_ID,    getCellId(this, 0));
            i.putExtra(DisplayActivity.EXTRA_SHOWCASE_API_TOKEN, getCellToken(this, 0));
            i.putExtra(DisplayActivity.EXTRA_SHOWCASE_API_URL,   getCellApiUrl(this, 0));
            i.putExtra(DisplayActivity.EXTRA_SHOWCASE_CELL,      0);
            Bitmap cached = loadCachedBitmap(this, 0);
            if (cached != null) {
                java.io.File f = new java.io.File(getFilesDir(), CACHE_PREFIX + "0.png");
                if (f.exists()) i.putExtra(DisplayActivity.EXTRA_SHOWCASE_PRELOAD_PATH, f.getAbsolutePath());
            }
            startActivity(i);
            finish();
            return;
        }

        loadCachedBitmaps();
        if (!anyCacheExists()) startFetchAll();
        forceFullRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If showcase mode was disabled (e.g. user saved credentials via web app),
        // finish this activity so DisplayActivity takes over cleanly.
        if (!ApiPrefs.isShowcaseModeEnabled(this)) {
            finish();
            return;
        }
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        // Refresh grid in case a cell was just updated by DisplayActivity
        loadCachedBitmaps();
        forceFullRefresh();
    }

    @Override
    protected void onDestroy() {
        cancelConnectivityWatch();
        if (noWifiDelayRunnable != null) handler.removeCallbacks(noWifiDelayRunnable);
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------
    // UI construction
    // ---------------------------------------------------------------------------

    private FrameLayout buildUI() {
        FrameLayout root = new FrameLayout(this);
        contentRoot = new LinearLayout(this);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        contentRoot.setBackgroundColor(0xFFFFFFFF);
        root.addView(contentRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        // Header bar
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setBackgroundColor(0xFFEEEEEE);
        headerRow.setPadding(16, 8, 16, 8);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView header = new TextView(this);
        header.setText("Pre-Loaded Screens");
        header.setTextSize(14);
        header.setTextColor(0xFF000000);
        headerRow.addView(header, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        if (ApiPrefs.isGiftModeEnabled(this)) {
            TextView customizeLink = new TextView(this);
            customizeLink.setText("Customize \u2192");
            customizeLink.setTextSize(13);
            customizeLink.setTextColor(0xFF444444);
            customizeLink.setPadding(16, 4, 4, 4);
            headerRow.addView(customizeLink, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            // Make the whole header row the tap target — thin strip on rotated e-ink
            // is hard to hit precisely, so a full-width touch area is more reliable.
            headerRow.setOnTouchListener(new android.view.View.OnTouchListener() {
                public boolean onTouch(View v, android.view.MotionEvent event) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        Intent i = new Intent(ShowcaseActivity.this, DisplayActivity.class);
                        i.putExtra("show_gift_screen", true);
                        startActivity(i);
                    }
                    return true;
                }
            });
        }

        contentRoot.addView(headerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 2x2 grid fills the remaining height exactly via weight=1
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBackgroundColor(0xFFFFFFFF);
        grid.setPadding(GRID_PADDING, GRID_PADDING, GRID_PADDING, GRID_PADDING);
        contentRoot.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, 0, 1.0f));

        LinearLayout row0 = makeGridRow(0, 1);
        grid.addView(row0, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, 0, 1.0f));

        View divH = new View(this);
        divH.setBackgroundColor(0xFFFFFFFF);
        grid.addView(divH, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, GRID_GAP));

        LinearLayout row1 = makeGridRow(2, 3);
        grid.addView(row1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, 0, 1.0f));

        return root;
    }

    private LinearLayout makeGridRow(final int cellA, final int cellB) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(0xFFCCCCCC);

        row.addView(makeCellView(cellA), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.FILL_PARENT, 1.0f));

        View divV = new View(this);
        divV.setBackgroundColor(0xFFFFFFFF);
        row.addView(divV, new LinearLayout.LayoutParams(
                GRID_GAP, ViewGroup.LayoutParams.FILL_PARENT));

        row.addView(makeCellView(cellB), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.FILL_PARENT, 1.0f));

        return row;
    }

    private FrameLayout makeCellView(final int cellIdx) {
        FrameLayout border = new FrameLayout(this);
        border.setBackgroundColor(0xFFAAAAAA);
        border.setPadding(1, 1, 1, 1);

        FrameLayout cell = new FrameLayout(this);
        cell.setBackgroundColor(0xFFFFFFFF);
        cell.setPadding(CELL_PADDING, CELL_PADDING, CELL_PADDING, CELL_PADDING);
        border.addView(cell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        RotateLayout imgRotate = new RotateLayout(this);
        imgRotate.setAngle((360 - APP_ROTATION_DEGREES) % 360);

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setVisibility(View.GONE);
        cellImageViews[cellIdx] = img;

        imgRotate.addView(img, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));
        cell.addView(imgRotate, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        // Status text shown when no cache exists
        TextView status = new TextView(this);
        status.setTextColor(0xFF666666);
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        boolean configured = isCellConfigured(this, cellIdx);
        status.setText(configured ? "Loading..." : "Not configured");
        status.setTextColor(configured ? 0xFF666666 : 0xFF999999);
        cellStatusViews[cellIdx] = status;
        cell.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT,
                Gravity.CENTER));

        // Title bar
        String cellNamePref = ApiPrefs.getCellName(this, cellIdx);
        String cellDisplayName = (cellNamePref != null && cellNamePref.length() > 0)
                ? cellNamePref : "Cell " + (cellIdx + 1);
        TextView title = new TextView(this);
        title.setText(cellDisplayName);
        title.setTextSize(15);
        title.setTextColor(0xFF000000);
        title.setBackgroundColor(0xFFDDDDDD);
        title.setPadding(4, 3, 4, 3);
        title.setGravity(Gravity.CENTER);
        int titleGravity = (cellIdx < 2) ? Gravity.TOP : Gravity.BOTTOM;
        cell.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                titleGravity));

        border.setClickable(true);
        border.setOnTouchListener(new android.view.View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    if (!isCellConfigured(ShowcaseActivity.this, cellIdx)) return true;
                    android.util.Log.d(TAG, "cell tapped: " + cellIdx);
                    FileLogger.d(TAG, "cell tapped: " + cellIdx);
                    openFullscreen(cellIdx);
                }
                return true;
            }
        });

        return border;
    }

    // ---------------------------------------------------------------------------
    // Cache load
    // ---------------------------------------------------------------------------

    private void loadCachedBitmaps() {
        for (int i = 0; i < NUM_CELLS; i++) {
            if (!isCellConfigured(this, i)) {
                // Leave status as "Not configured", image hidden
                continue;
            }
            Bitmap bmp = loadCachedBitmap(this, i);
            if (bmp == null) continue;
            cellBitmaps[i] = bmp;
            ImageView img = cellImageViews[i];
            TextView status = cellStatusViews[i];
            if (img != null) { img.setImageBitmap(bmp); img.setVisibility(View.VISIBLE); }
            if (status != null) status.setVisibility(View.GONE);
        }
    }

    /** Returns true if at least one cell has a cached bitmap on disk. */
    private boolean anyCacheExists() {
        for (int i = 0; i < NUM_CELLS; i++) {
            if (!isCellConfigured(this, i)) continue;
            if (loadCachedBitmap(this, i) != null) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------------
    // Fetch all cells (first boot / no cache)
    // ---------------------------------------------------------------------------

    private boolean[] cellLoading = new boolean[NUM_CELLS];
    private android.content.BroadcastReceiver connectivityReceiver;
    private Runnable noWifiDelayRunnable;
    private View noWifiOverlay;
    private static final long WIFI_WARMUP_MS = 15 * 1000;
    private final android.os.Handler handler = new android.os.Handler();

    private static final long CELL_FETCH_STAGGER_MS = 8000;

    private void startFetchAll() {
        if (isConnected()) {
            // Stagger fetches to avoid overwhelming the network with 4 simultaneous TLS handshakes
            for (int i = 0; i < NUM_CELLS; i++) {
                final int cellIdx = i;
                handler.postDelayed(new Runnable() {
                    public void run() {
                        if (isCellConfigured(ShowcaseActivity.this, cellIdx)) fetchCell(cellIdx);
                    }
                }, i * CELL_FETCH_STAGGER_MS);
            }
            return;
        }
        // Enable WiFi if off
        android.net.wifi.WifiManager wm =
                (android.net.wifi.WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
        if (wm != null && !wm.isWifiEnabled()) wm.setWifiEnabled(true);
        // Wait for connection, then fetch
        watchForConnectivity();
        if (noWifiDelayRunnable != null) handler.removeCallbacks(noWifiDelayRunnable);
        final ShowcaseActivity self = this;
        noWifiDelayRunnable = new Runnable() {
            public void run() {
                noWifiDelayRunnable = null;
                if (!isConnected()) showNoWifiScreen();
            }
        };
        handler.postDelayed(noWifiDelayRunnable, WIFI_WARMUP_MS);
    }

    private void fetchCell(final int cellIdx) {
        if (!isCellConfigured(this, cellIdx)) return;
        if (cellLoading[cellIdx]) return;
        cellLoading[cellIdx] = true;
        new FetchTask(this, cellIdx, getCellId(this, cellIdx), getCellToken(this, cellIdx), getCellApiUrl(this, cellIdx)).execute();
    }

    private void onFetchComplete(int cellIdx, Bitmap bitmap, String error) {
        cellLoading[cellIdx] = false;
        if (bitmap != null) {
            hideNoWifiScreen();
            cellBitmaps[cellIdx] = bitmap;
            saveCachedBitmap(this, cellIdx, bitmap);
            ImageView img = cellImageViews[cellIdx];
            TextView status = cellStatusViews[cellIdx];
            if (img != null) { img.setImageBitmap(bitmap); img.setVisibility(View.VISIBLE); }
            if (status != null) status.setVisibility(View.GONE);
            forceFullRefresh();
        } else {
            if (!isConnected()) { showNoWifiScreen(); return; }
            TextView status = cellStatusViews[cellIdx];
            if (status != null) {
                status.setText(error != null ? error : "Error");
                status.setVisibility(View.VISIBLE);
            }
        }
    }

    private boolean isConnected() {
        android.net.ConnectivityManager cm =
                (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void showNoWifiScreen() {
        if (noWifiOverlay != null && noWifiOverlay.getVisibility() == View.VISIBLE) return;
        android.widget.LinearLayout overlay = new android.widget.LinearLayout(this);
        overlay.setOrientation(android.widget.LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(0xFFFFFFFF);
        overlay.setPadding(40, 60, 40, 40);
        android.widget.TextView msg = new android.widget.TextView(this);
        msg.setText("This smart display needs WiFi.");
        msg.setTextSize(18);
        msg.setTextColor(0xFF000000);
        msg.setGravity(Gravity.CENTER);
        overlay.addView(msg, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.TextView sub = new android.widget.TextView(this);
        sub.setText("Once connected, screens will load automatically.");
        sub.setTextSize(13);
        sub.setTextColor(0xFF555555);
        sub.setGravity(Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams subParams = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = 12;
        overlay.addView(sub, subParams);
        android.widget.Button wifiBtn = new android.widget.Button(this);
        wifiBtn.setText("Wi-Fi Settings");
        wifiBtn.setTextColor(0xFF000000);
        wifiBtn.setBackgroundColor(0xFFDDDDDD);
        wifiBtn.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    new AlertDialog.Builder(ShowcaseActivity.this)
                        .setTitle("Opening Wi-Fi Settings")
                        .setMessage("After connecting, press the Home button on the bottom of this device to return to this app.")
                        .setPositiveButton("Open Wi-Fi Settings", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Intent wi = new Intent();
                                    wi.setClassName("com.android.settings", "com.android.settings.wifi.Settings_Wifi_Settings");
                                    try { startActivity(wi); } catch (Throwable t2) {
                                        startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                                    }
                                } catch (Throwable t) {}
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                }
                return true;
            }
        });
        android.widget.LinearLayout.LayoutParams btnParams = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = 24;
        overlay.addView(wifiBtn, btnParams);
        if (noWifiOverlay != null && noWifiOverlay.getParent() != null)
            ((ViewGroup) noWifiOverlay.getParent()).removeView(noWifiOverlay);
        noWifiOverlay = overlay;
        if (contentRoot != null && contentRoot.getParent() instanceof FrameLayout) {
            ((FrameLayout) contentRoot.getParent()).addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        }
        forceFullRefresh();
        watchForConnectivity();
    }

    private void hideNoWifiScreen() {
        if (noWifiOverlay == null) return;
        noWifiOverlay.setVisibility(View.GONE);
        cancelConnectivityWatch();
        if (noWifiDelayRunnable != null) { handler.removeCallbacks(noWifiDelayRunnable); noWifiDelayRunnable = null; }
    }

    private void watchForConnectivity() {
        if (connectivityReceiver != null) return;
        connectivityReceiver = new android.content.BroadcastReceiver() {
            public void onReceive(android.content.Context context, Intent intent) {
                if (!isConnected()) return;
                handler.post(new Runnable() {
                    public void run() {
                        hideNoWifiScreen();
                        for (int i = 0; i < NUM_CELLS; i++) {
                            final int cellIdx = i;
                            handler.postDelayed(new Runnable() {
                                public void run() {
                                    if (isCellConfigured(ShowcaseActivity.this, cellIdx)) fetchCell(cellIdx);
                                }
                            }, cellIdx * CELL_FETCH_STAGGER_MS);
                        }
                    }
                });
            }
        };
        try {
            registerReceiver(connectivityReceiver,
                    new android.content.IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));
        } catch (Throwable t) {}
    }

    private void cancelConnectivityWatch() {
        if (connectivityReceiver == null) return;
        try { unregisterReceiver(connectivityReceiver); } catch (Throwable ignored) {}
        connectivityReceiver = null;
    }

    // ---------------------------------------------------------------------------
    // AsyncTask — fetch one cell
    // ---------------------------------------------------------------------------

    private static class FetchTask extends android.os.AsyncTask<Void, Void, FetchTask.FetchResult> {
        static class FetchResult { Bitmap bitmap; String error; }

        private final java.lang.ref.WeakReference<ShowcaseActivity> activityRef;
        private final int cellIdx;
        private final String deviceId;
        private final String deviceToken;
        private final String apiUrl;

        FetchTask(ShowcaseActivity activity, int cellIdx, String deviceId, String deviceToken, String apiUrl) {
            this.activityRef = new java.lang.ref.WeakReference<ShowcaseActivity>(activity);
            this.cellIdx = cellIdx;
            this.deviceId = deviceId;
            this.deviceToken = deviceToken;
            this.apiUrl = apiUrl;
        }

        protected FetchResult doInBackground(Void... params) {
            FetchResult result = new FetchResult();
            ShowcaseActivity activity = activityRef.get();
            if (activity == null) { result.error = "cancelled"; return result; }
            try {
                java.util.Hashtable headers = new java.util.Hashtable();
                headers.put("ID", deviceId);
                headers.put("access-token", deviceToken);
                headers.put("User-Agent", "TRMNL-Nook/1.0 (Android 2.1)");
                headers.put("Accept", "application/json");
                headers.put("Percent-Charged", "80");
                headers.put("rssi", "-65");
                String jsonText = null;
                for (int attempt = 1; attempt <= 2; attempt++) {
                    if (attempt > 1) try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    String resp = BouncyCastleHttpClient.getHttps(activity, apiUrl, headers);
                    if (resp != null && resp.length() > 0 && !resp.startsWith("Error:")) { jsonText = resp; break; }
                }
                if (jsonText == null) { result.error = "No response"; return result; }
                TrmnlApiResponseParser.Logger logger = new TrmnlApiResponseParser.Logger() {
                    public void logD(String msg) { android.util.Log.d(TAG, "[cell" + cellIdx + "] " + msg); }
                    public void logW(String msg) { android.util.Log.w(TAG, "[cell" + cellIdx + "] " + msg); }
                };
                TrmnlApiResponseParser.Result parsed =
                        TrmnlApiResponseParser.parseAndMaybeFetchImage(activity, jsonText, logger);
                if (parsed.showImage && parsed.bitmap != null) result.bitmap = parsed.bitmap;
                else result.error = "No image";
            } catch (Throwable t) {
                result.error = t.getMessage();
                android.util.Log.w(TAG, "FetchTask cell " + cellIdx + ": " + t);
            }
            return result;
        }

        protected void onPostExecute(FetchResult result) {
            ShowcaseActivity activity = activityRef.get();
            if (activity != null) activity.onFetchComplete(cellIdx, result.bitmap, result.error);
        }
    }

    // ---------------------------------------------------------------------------
    // Fullscreen
    // ---------------------------------------------------------------------------

    private void openFullscreen(int cellIdx) {
        if (!isCellConfigured(this, cellIdx)) return;
        Intent i = new Intent(this, DisplayActivity.class);
        i.putExtra(DisplayActivity.EXTRA_SHOWCASE_API_ID,    getCellId(this, cellIdx));
        i.putExtra(DisplayActivity.EXTRA_SHOWCASE_API_TOKEN, getCellToken(this, cellIdx));
        i.putExtra(DisplayActivity.EXTRA_SHOWCASE_API_URL,   getCellApiUrl(this, cellIdx));
        i.putExtra(DisplayActivity.EXTRA_SHOWCASE_CELL,      cellIdx);
        // Pass cached bitmap path so DisplayActivity can show it instantly
        Bitmap bmp = cellBitmaps[cellIdx];
        if (bmp != null) {
            // The cache file is already on disk — just pass its path directly
            java.io.File f = new java.io.File(getFilesDir(), CACHE_PREFIX + cellIdx + ".png");
            if (f.exists()) i.putExtra(DisplayActivity.EXTRA_SHOWCASE_PRELOAD_PATH, f.getAbsolutePath());
        }
        startActivity(i);
    }

    // ---------------------------------------------------------------------------
    // E-ink refresh
    // ---------------------------------------------------------------------------

    private void forceFullRefresh() {
        View root = getWindow().getDecorView();
        if (root == null) return;
        root.invalidate();
        root.requestLayout();
        root.post(new Runnable() {
            public void run() {
                triggerEpdRefresh();
            }
        });
    }

    private void triggerEpdRefresh() {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(
                "/sys/devices/platform/omap3epfb.0/graphics/fb0/epd_refresh");
            fw.write("1");
            fw.close();
        } catch (Exception ignored) {}
    }
}
