package com.bpmct.trmnl_nook_simple_touch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;

// Local helper for parsing TRMNL API responses + downloading images.
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;

public class DisplayActivity extends Activity {
    public static final String EXTRA_CLEAR_IMAGE = "clear_image";
    public static final String EXTRA_SHOWCASE_API_ID      = "showcase_api_id";
    public static final String EXTRA_SHOWCASE_API_TOKEN   = "showcase_api_token";
    public static final String EXTRA_SHOWCASE_API_URL     = "showcase_api_url";
    public static final int    EXTRA_SHOWCASE_CELL_NONE   = -1;
    public static final String EXTRA_SHOWCASE_CELL        = "showcase_cell";
    /** Absolute path to a pre-rendered PNG from the showcase grid; show it immediately, skip initial fetch. */
    public static final String EXTRA_SHOWCASE_PRELOAD_PATH = "showcase_preload_path";
    private static final String TAG = "TRMNLAPI";
    private static final long DEFAULT_REFRESH_MS = 15 * 60 * 1000;
    private static final String API_DISPLAY_PATH = "/display";
    private static final String ALARM_REFRESH_ACTION = "com.bpmct.trmnl_nook_simple_touch.ALARM_REFRESH_ACTION";
    private static final int MAX_WIFI_RECOVERY_ATTEMPTS = 2;
    /** When true, skip API and show generic on screen (for testing). When false, foreground = API image, screensaver file = generic. */
    private static final boolean USE_GENERIC_IMAGE = false;
    /** Delay after showing API image before writing screensaver and going to sleep (show picture, then screensaver, then sleep full interval). */
    private static final long SCREENSAVER_DELAY_MS = 2 * 1000;
    private static final long WIFI_RECOVERY_TOGGLE_DELAY_MS = 2 * 1000;
    private TextView contentView;
    private TextView logView;
    private ImageView imageView;
    private ScrollView contentScroll;
    private RotateLayout appRotateLayout;
    private FrameLayout rootLayout;
    private View noWifiOverlay;
    private LinearLayout menuLayout;
    private LinearLayout bootLayout;
    private TextView bootStatus;
    private boolean bootComplete = false;
    private View menuScrim;
    private View flashOverlay;
    private TextView batteryView;
    private Button nextButton;
    private Button settingsButton;
    private Button customizeButton;
    private TextView loadingStatusView;
    private TextView showcaseStatusView;
    private RotateLayout imageRotateLayout;
    private boolean menuVisible = false;
    private boolean giftScreenVisible = false;
    private final Handler refreshHandler = new Handler();
    private Runnable refreshRunnable;
    private volatile boolean fetchInProgress = false;
    private volatile boolean fetchStartedFromMenu = false;
    private volatile long refreshMs = DEFAULT_REFRESH_MS;
    /** Last displayed API image; used for screensaver file when allow-sleep + write-screensaver. */
    private Bitmap lastDisplayedImage;
    /** Reason for current fetch (for logging) */
    private volatile String fetchReason = "unknown";
    private String showcaseApiId;    // non-null when running as showcase cell
    private String showcaseApiToken;
    private String showcaseApiUrl;   // per-cell API URL (e.g. larapaper endpoint)
    private int    showcaseCell = EXTRA_SHOWCASE_CELL_NONE;
    private final StringBuilder logBuffer = new StringBuilder();
    private static final int MAX_LOG_CHARS = 6000;
    private static final int APP_ROTATION_DEGREES = 90;

    private AlarmManager alarmManager;
    private PendingIntent alarmPendingIntent;
    private BroadcastReceiver alarmReceiver;
    private BroadcastReceiver connectivityReceiver;
    private Runnable pendingSleepRunnable;
    private Runnable pendingScreenOffRunnable;
    /** True while sleepNow() is armed — blocks onResume from re-asserting FLAG_KEEP_SCREEN_ON
     * and restoring the 120s screen timeout until the device actually wakes. */
    private volatile boolean sleepPending = false;
    private Runnable pendingWifiRecoveryRunnable;
    private Runnable pendingWifiWarmupRunnable;
    private Runnable pendingConnectivityTimeoutRunnable;
    private static final long CONNECTIVITY_MAX_WAIT_MS = 5 * 1000;
    private volatile int wifiRecoveryAttempts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize file logging from saved preference
        FileLogger.setEnabled(ApiPrefs.isFileLoggingEnabled(this));
        logD("onCreate pid=" + android.os.Process.myPid()
                + " allow_sleep=" + ApiPrefs.isAllowSleep(this)
                + " super_sleep=" + ApiPrefs.isSuperSleep(this)
                + " auto_disable_wifi=" + ApiPrefs.isAutoDisableWifi(this)
                + " allow_http=" + ApiPrefs.isAllowHttp(this));

        // Write the generic screensaver on first-ever launch so NOOK shows something
        // branded if it sleeps before any API image has been displayed.
        if (!ApiPrefs.isScreensaverWrittenOnce(this)) {
            writeGenericScreensaver();
            ApiPrefs.setScreensaverWrittenOnce(this, true);
        }

        // NOOK Simple Touch is API 7 (no nav bar); keep this deterministic.
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

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

        // Boot header: [icon] TRMNL / status
        bootLayout = new LinearLayout(this);
        bootLayout.setOrientation(LinearLayout.HORIZONTAL);
        bootLayout.setGravity(Gravity.CENTER_VERTICAL);
        bootLayout.setPadding(20, 20, 20, 10);
        
        ImageView bootIcon = new ImageView(this);
        bootIcon.setImageResource(R.drawable.ic_launcher);
        bootLayout.addView(bootIcon);
        
        bootStatus = new TextView(this);
        bootStatus.setText("TRMNL  Starting...");
        bootStatus.setTextColor(0xFF000000);
        bootStatus.setTextSize(16);
        bootStatus.setPadding(15, 0, 0, 0);
        bootLayout.addView(bootStatus);
        
        contentLayout.addView(bootLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        logView = new TextView(this);
        logView.setPadding(20, 10, 20, 20);
        logView.setTextColor(0xFF000000); // Black text for e-ink
        logView.setTextSize(11);
        logView.setText("");
        // Logs stream during boot below the header
        contentLayout.addView(logView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                0, 1.0f));

        imageRotateLayout = new RotateLayout(this);
        imageRotateLayout.setAngle((360 - APP_ROTATION_DEGREES) % 360);
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        imageView.setVisibility(View.GONE);
        imageRotateLayout.setVisibility(View.GONE); // Hidden during boot so logView gets full height
        imageRotateLayout.addView(imageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));
        contentLayout.addView(imageRotateLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                0,
                1.0f));

        contentScroll = new ScrollView(this);
        contentScroll.setVisibility(View.GONE); // Hidden during boot
        contentView = new TextView(this);
        contentView.setPadding(20, 20, 20, 20);
        contentView.setTextColor(0xFF000000); // Black text for e-ink
        contentView.setTextSize(16);
        contentView.setText("");
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

        // Menu sits OUTSIDE the rotated root so MATCH_PARENT resolves to
        // physical screen width (600px), not the rotated child width (800px).
        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.HORIZONTAL);
        menuLayout.setVisibility(View.GONE);
        menuLayout.setClickable(true);
        menuLayout.setFocusable(true);

        menuLayout.setPadding(8, 4, 8, 4);
        menuLayout.setBackgroundColor(0xFFEFEFEF);
        menuLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        batteryView = new TextView(this);
        batteryView.setTextColor(0xFF000000);
        batteryView.setTextSize(16);
        batteryView.setText("Battery: --%");
        batteryView.setGravity(Gravity.CENTER_VERTICAL);
        menuLayout.addView(batteryView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 40));

        nextButton = new Button(this);
        nextButton.setText("Next");
        nextButton.setTextColor(0xFF000000);
        nextButton.setClickable(true);
        nextButton.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    logD("menu: next tapped");
                    if (USE_GENERIC_IMAGE) {
                        hideMenu();
                        showGenericImageAndSleep();
                    } else {
                        fetchReason = "menu-next";
                        showMenuStatus("Loading...", false);
                        startFetch();
                    }
                    return true;
                }
                return false;
            }
        });
        nextButton.setTextSize(15);
        nextButton.setPadding(4, 0, 4, 0);
        nextButton.setMinHeight(0);
        nextButton.setMinimumHeight(0);
        nextButton.setMinWidth(0);
        nextButton.setMinimumWidth(0);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                0, 40, 1.0f);
        nextParams.leftMargin = 6;
        menuLayout.addView(nextButton, nextParams);

        settingsButton = new Button(this);
        settingsButton.setText("Settings");
        settingsButton.setTextColor(0xFF000000);
        settingsButton.setClickable(true);
        settingsButton.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    logD("menu: settings tapped");
                    hideMenu();
                    try {
                        startActivity(new Intent(DisplayActivity.this, SettingsActivity.class));
                    } catch (Throwable t) {
                        logW("settings launch failed: " + t);
                    }
                    return true;
                }
                return false;
            }
        });
        settingsButton.setTextSize(15);
        settingsButton.setPadding(4, 0, 4, 0);
        settingsButton.setMinHeight(0);
        settingsButton.setMinimumHeight(0);
        settingsButton.setMinWidth(0);
        settingsButton.setMinimumWidth(0);
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                0, 40, 1.0f);
        settingsParams.leftMargin = 6;
        menuLayout.addView(settingsButton, settingsParams);

        customizeButton = new Button(this);
        customizeButton.setText("Customize");
        customizeButton.setTextColor(0xFF000000);
        customizeButton.setClickable(true);
        customizeButton.setVisibility(View.GONE);
        customizeButton.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    hideMenu();
                    showGiftModeScreen();
                    return true;
                }
                return false;
            }
        });
        customizeButton.setTextSize(16);
        customizeButton.setPadding(4, 0, 4, 0);
        customizeButton.setMinHeight(0);
        customizeButton.setMinimumHeight(0);
        customizeButton.setMinWidth(0);
        customizeButton.setMinimumWidth(0);
        LinearLayout.LayoutParams customizeParams = new LinearLayout.LayoutParams(
                0, 40, 1.0f);
        customizeParams.leftMargin = 6;
        menuLayout.addView(customizeButton, customizeParams);

        loadingStatusView = new TextView(this);
        loadingStatusView.setTextColor(0xFF000000);
        loadingStatusView.setTextSize(16);
        loadingStatusView.setPadding(8, 0, 8, 0);
        loadingStatusView.setVisibility(View.GONE);
        menuLayout.addView(loadingStatusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // The root child is 800×600 in child space (90° rotation maps:
        //   child-X → physical-Y,  child-Y → physical-X).
        // Menu must fill child HEIGHT (= physical width, 600px) → MATCH_PARENT height.
        // Center in child WIDTH (= physical height, 800px) → Gravity.CENTER_HORIZONTAL.
        menuLayout.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                480,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(menuLayout, menuParams);
        menuLayout.bringToFront();

        // Showcase fetch status bar — shown at bottom of screen during preload-refresh
        // when super sleep is enabled, so user knows a flash is coming.
        showcaseStatusView = new TextView(this);
        showcaseStatusView.setText("Loading latest image... then will sleep.");
        showcaseStatusView.setTextColor(0xFF000000);
        showcaseStatusView.setTextSize(13);
        showcaseStatusView.setGravity(Gravity.CENTER);
        showcaseStatusView.setBackgroundColor(0xFFEEEEEE);
        showcaseStatusView.setPadding(16, 8, 16, 8);
        showcaseStatusView.setVisibility(View.GONE);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(showcaseStatusView, statusParams);
        showcaseStatusView.bringToFront();

        appRotateLayout = new RotateLayout(this);
        appRotateLayout.setAngle(APP_ROTATION_DEGREES);
        appRotateLayout.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT));

        setContentView(appRotateLayout);

        applyShowcaseExtras(getIntent());

        // Alarm + receiver for wake-from-sleep refresh (Electric-Sign pattern).
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ALARM_REFRESH_ACTION), PendingIntent.FLAG_CANCEL_CURRENT);
        alarmReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                DisplayActivity a = DisplayActivity.this;
                if (ApiPrefs.isAllowSleep(a)) {
                    setKeepScreenAwake(true);
                }
                if (USE_GENERIC_IMAGE) {
                    showGenericImageAndSleep();
                    return;
                }
                if (fetchInProgress) {
                    logD("alarm: fetch already in progress, skipping");
                    return;
                }
                // If running as a showcase cell, advance to the next cell before fetching
                if (isShowcaseCell()) {
                    int nextCell = (showcaseCell + 1) % ShowcaseActivity.NUM_CELLS;
                    showcaseCell     = nextCell;
                    showcaseApiId    = ShowcaseActivity.getCellId(a, nextCell);
                    showcaseApiToken = ShowcaseActivity.getCellToken(a, nextCell);
                    showcaseApiUrl   = ShowcaseActivity.getCellApiUrl(a, nextCell);
                }
                fetchReason = "alarm";
                WifiManager wifi = (WifiManager) a.getSystemService(Context.WIFI_SERVICE);
                if (!isConnectedToNetwork(a)) {
                    if (wifi != null && !wifi.isWifiEnabled()) wifi.setWifiEnabled(true);
                    a.waitForWifiThenFetch();
                    return;
                }
                startFetch();
            }
        };
        registerReceiver(alarmReceiver, new IntentFilter(ALARM_REFRESH_ACTION));

        setKeepScreenAwake(true);

        // Gift mode shows a static screen — never needs WiFi. But showcase cells always need WiFi.
        boolean wifiJustOn = (ApiPrefs.isGiftModeEnabled(this) && !isShowcaseCell()) ? false : ensureWifiOnWhenForeground();

        // Initial display: showcase grid first; gift screen accessible via "Customize" in header.
        if (!isShowcaseCell() && getIntent().getBooleanExtra("show_gift_screen", false)) {
            showGiftModeScreen();
            return;
        } else if (!isShowcaseCell() && ApiPrefs.isShowcaseModeEnabled(this)) {
            if (ShowcaseActivity.isOnlyCell0Configured(this)) {
                // Single cell configured — launch it fullscreen directly, skip the grid
                Intent si = new Intent(this, DisplayActivity.class);
                si.putExtra(EXTRA_SHOWCASE_API_ID,    ShowcaseActivity.getCellId(this, 0));
                si.putExtra(EXTRA_SHOWCASE_API_TOKEN, ShowcaseActivity.getCellToken(this, 0));
                si.putExtra(EXTRA_SHOWCASE_API_URL,   ShowcaseActivity.getCellApiUrl(this, 0));
                si.putExtra(EXTRA_SHOWCASE_CELL,      0);
                startActivity(si);
                finish();
            } else {
                startActivity(new Intent(this, ShowcaseActivity.class));
                finish();
            }
            return;
        } else if (!isShowcaseCell() && ApiPrefs.isGiftModeEnabled(this)) {
            showGiftModeScreen();
            return;
        } else if (USE_GENERIC_IMAGE) {
            showGenericImageAndSleep();
        } else if (isShowcaseCell() && getIntent().hasExtra(EXTRA_SHOWCASE_PRELOAD_PATH)) {
            // Bitmap already fetched in the showcase grid — display it instantly, no fetch needed.
            final String preloadPath = getIntent().getStringExtra(EXTRA_SHOWCASE_PRELOAD_PATH);
            refreshHandler.post(new Runnable() {
                public void run() {
                    Bitmap bmp = null;
                    try {
                        bmp = android.graphics.BitmapFactory.decodeFile(preloadPath);
                    } catch (Throwable t) {
                        logW("preload decode failed: " + t);
                    }
                    if (bmp != null) {
                        displayPreloadedImage(bmp);
                    } else {
                        // Fall back to a normal fetch if decode fails
                        fetchReason = "preload-fallback";
                        startFetch();
                    }
                }
            });
        } else if (ensureCredentials()) {
            fetchReason = "onCreate";
            if (wifiJustOn) {
                waitForWifiThenFetch();
            } else {
                startFetch();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        logD("onResume pid=" + android.os.Process.myPid()
                + " fetchInProgress=" + fetchInProgress
                + " wifi=" + getWifiStateString());
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        sleepPending = false;
        try {
            android.provider.Settings.System.putInt(
                getContentResolver(),
                android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
                120000);
        } catch (Throwable t) { /* ignore */ }
        setKeepScreenAwake(true);

        // Gift mode shows a static screen — never needs WiFi. But showcase cells always need WiFi.
        boolean wifiJustOn = (ApiPrefs.isGiftModeEnabled(this) && !isShowcaseCell()) ? false : ensureWifiOnWhenForeground();

        applyIntentState(getIntent());
        if (!isShowcaseCell()) {
            applyShowcaseExtras(getIntent());
        }
        if (!isShowcaseCell() && getIntent().getBooleanExtra("show_gift_screen", false)) {
            showGiftModeScreen();
            return;
        } else if (!isShowcaseCell() && ApiPrefs.isShowcaseModeEnabled(this)) {
            if (ShowcaseActivity.isOnlyCell0Configured(this)) {
                Intent si = new Intent(this, DisplayActivity.class);
                si.putExtra(EXTRA_SHOWCASE_API_ID,    ShowcaseActivity.getCellId(this, 0));
                si.putExtra(EXTRA_SHOWCASE_API_TOKEN, ShowcaseActivity.getCellToken(this, 0));
                si.putExtra(EXTRA_SHOWCASE_API_URL,   ShowcaseActivity.getCellApiUrl(this, 0));
                si.putExtra(EXTRA_SHOWCASE_CELL,      0);
                startActivity(si);
                finish();
            } else {
                startActivity(new Intent(this, ShowcaseActivity.class));
                finish();
            }
            return;
        } else if (!isShowcaseCell() && ApiPrefs.isGiftModeEnabled(this)) {
            showGiftModeScreen();
            return;
        } else if (USE_GENERIC_IMAGE) {
            showGenericImageAndSleep();
        } else if (ensureCredentials()) {
            if (!fetchInProgress) {
                // Showcase cell: if we already have an image from the preload, don't
                // immediately re-fetch — scheduleNextCycle() from displayPreloadedImage
                // will handle the next refresh at the normal interval.
                if (isShowcaseCell() && lastDisplayedImage != null) {
                    logD("onResume: showcase cell has preloaded image, skipping immediate fetch");
                } else {
                    fetchReason = "onResume";
                    if (wifiJustOn) {
                        waitForWifiThenFetch();
                    } else {
                        startFetch();
                    }
                }
            } else {
                logD("onResume: fetch already in progress, skipping");
            }
            // Don't schedule here - fetch completion will schedule the next refresh
        }
    }

    /** When we just turned WiFi on, delay fetch so connection can establish. */
    private void scheduleFetchAfterWifiWarmup() {
        if (pendingWifiWarmupRunnable != null) {
            refreshHandler.removeCallbacks(pendingWifiWarmupRunnable);
        }
        pendingWifiWarmupRunnable = new Runnable() {
            @Override
            public void run() {
                pendingWifiWarmupRunnable = null;
                startFetch();
            }
        };
        refreshHandler.postDelayed(pendingWifiWarmupRunnable, WIFI_WARMUP_MS);
        logD("fetch in " + (WIFI_WARMUP_MS / 1000L) + "s (wifi warming up)");
    }

    private void cancelWifiRecovery() {
        if (pendingWifiRecoveryRunnable != null) {
            refreshHandler.removeCallbacks(pendingWifiRecoveryRunnable);
            pendingWifiRecoveryRunnable = null;
        }
    }

    private boolean attemptWifiRecovery(final String reason) {
        final WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifi == null) {
            logW("wifi recovery unavailable: wifi manager null");
            return false;
        }
        if (wifiRecoveryAttempts >= MAX_WIFI_RECOVERY_ATTEMPTS) {
            logW("wifi recovery exhausted after " + wifiRecoveryAttempts + " attempts");
            return false;
        }
        cancelConnectivityWait();
        cancelWifiRecovery();
        wifiRecoveryAttempts++;
        final int attempt = wifiRecoveryAttempts;
        logW("wifi recovery attempt " + attempt + "/" + MAX_WIFI_RECOVERY_ATTEMPTS + " (" + reason + "), state=" + getWifiStateString());
        try {
            wifi.setWifiEnabled(false);
            logD("wifi off for recovery");
        } catch (Throwable t) {
            logW("wifi off for recovery failed: " + t);
        }
        pendingWifiRecoveryRunnable = new Runnable() {
            @Override
            public void run() {
                pendingWifiRecoveryRunnable = null;
                try {
                    wifi.setWifiEnabled(true);
                    logD("wifi on for recovery");
                } catch (Throwable t) {
                    logW("wifi on for recovery failed: " + t);
                }
                waitForWifiThenFetch();
            }
        };
        refreshHandler.postDelayed(pendingWifiRecoveryRunnable, WIFI_RECOVERY_TOGGLE_DELAY_MS);
        return true;
    }

    /** Wait for network to come up, then start fetch. Starts as soon as connectivity appears; max wait CONNECTIVITY_MAX_WAIT_MS. */
    private void waitForWifiThenFetch() {
        cancelConnectivityWait();
        if (isConnectedToNetwork(this)) {
            startFetch();
            return;
        }
        // Hide the showcase status banner immediately — no-wifi overlay needs to be visible.
        // Exception: if we already have a cached image on screen (preload path), keep the
        // "Loading latest image" banner visible so the user knows a refresh is in progress.
        if (showcaseStatusView != null && lastDisplayedImage == null) {
            showcaseStatusView.setVisibility(View.GONE);
        }
        final DisplayActivity a = this;
        WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        // If WiFi radio is off entirely, nothing to wait for — show overlay immediately.
        if (wm != null && !wm.isWifiEnabled()) {
            logD("wifi radio off — showing no-wifi screen immediately");
            showNoWifiScreen();
            scheduleNextCycle();
            return;
        }
        // Fast-path: WiFi is on and already has an IP — just fetch now.
        if (wm != null && wm.isWifiEnabled()) {
            WifiInfo wi = wm.getConnectionInfo();
            if (wi != null && wi.getIpAddress() != 0) {
                logD("wifi has IP, skipping wait — starting fetch");
                startFetch();
                return;
            }
        }
        // WiFi is on but not yet associated (e.g. device just woke from sleep).
        // Wait silently — connectivity receiver fires startFetch as soon as we associate.
        if (menuVisible) {
            showMenuStatus("Connecting\u2026", false);
        }
        connectivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!isConnectedToNetwork(context)) return;
                refreshHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (connectivityReceiver == null) return;
                        logD("connected, starting fetch");
                        cancelConnectivityWait();
                        startFetch();
                    }
                });
            }
        };
        try {
            registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        } catch (Throwable t) {
            logW("register connectivity receiver: " + t);
            scheduleFetchAfterWifiWarmup();
            return;
        }
        // Hard timeout — show no-wifi screen then schedule a retry so we don't get stuck.
        pendingConnectivityTimeoutRunnable = new Runnable() {
            @Override public void run() {
                pendingConnectivityTimeoutRunnable = null;
                if (!isConnectedToNetwork(a)) {
                    logD("connectivity timeout (" + (CONNECTIVITY_MAX_WAIT_MS / 1000L) + "s) wifi=" + a.getWifiStateString() + " — showing no-wifi screen");
                    cancelConnectivityWait();
                    if (attemptWifiRecovery("connectivity timeout")) {
                        return;
                    }
                    wifiRecoveryAttempts = 0;
                    a.showNoWifiScreen();
                    a.scheduleNextCycle();
                }
            }
        };
        refreshHandler.postDelayed(pendingConnectivityTimeoutRunnable, CONNECTIVITY_MAX_WAIT_MS);
        logD("not connected — waiting up to " + (CONNECTIVITY_MAX_WAIT_MS / 1000L) + "s for association");
    }

    private void cancelConnectivityWait() {
        if (connectivityReceiver != null) {
            try {
                unregisterReceiver(connectivityReceiver);
            } catch (Throwable t) {
                Log.w(TAG, "unregister connectivityReceiver: " + t);
            }
            connectivityReceiver = null;
        }
        if (pendingConnectivityTimeoutRunnable != null) {
            refreshHandler.removeCallbacks(pendingConnectivityTimeoutRunnable);
            pendingConnectivityTimeoutRunnable = null;
        }
    }

    /** Show connecting message when waiting for WiFi; keep dialog clean (no log). */
    private void showWarmupLoadingMessage() {
        if (contentView != null) contentView.setText("Connecting…");
        if (contentScroll != null) contentScroll.setVisibility(View.VISIBLE);
        if (imageView != null) imageView.setVisibility(View.GONE);
        if (logView != null) logView.setVisibility(View.GONE);
        // Skip EPD refresh — transient state, image replaces it immediately
    }

    /** WiFi stays on always; this is a no-op kept for call-site compatibility.
     * @return always false */
    private boolean ensureWifiOnWhenForeground() {
        if (!ApiPrefs.isAllowSleep(this)) return false;
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifi != null && !wifi.isWifiEnabled()) {
            wifi.setWifiEnabled(true);
            logD("wifi on (app in foreground), wait ~15s for connection");
            return true;
        }
        return false;
    }

    private static final long WIFI_WARMUP_MS = 15 * 1000;

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        ensureWifiOnWhenForeground();
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
        if ("sleep".equals(intent.getStringExtra("action"))) {
            intent.removeExtra("action");
            refreshHandler.post(new Runnable() {
                public void run() { sleepNow(); }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Restore screen timeout whenever we leave — user is navigating around the device.
        try {
            android.provider.Settings.System.putInt(
                getContentResolver(),
                android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
                120000);
        } catch (Throwable t) { /* ignore */ }
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
            logD("onPause: refresh timer cancelled (fetchInProgress=" + fetchInProgress + ")");
        }
        if (pendingSleepRunnable != null) {
            refreshHandler.removeCallbacks(pendingSleepRunnable);
            pendingSleepRunnable = null;
        }
        if (pendingScreenOffRunnable != null) {
            refreshHandler.removeCallbacks(pendingScreenOffRunnable);
            pendingScreenOffRunnable = null;
        }
        if (pendingWifiWarmupRunnable != null) {
            refreshHandler.removeCallbacks(pendingWifiWarmupRunnable);
            pendingWifiWarmupRunnable = null;
        }
        // Only cancel connectivity wait if no fetch is in progress — otherwise
        // a menu dismiss (onPause/onResume) would kill a "Connecting..." wait mid-fetch.
        if (!fetchInProgress) {
            cancelConnectivityWait();
        }
    }

    @Override
    protected void onDestroy() {
        logD("onDestroy pid=" + android.os.Process.myPid());
        cancelConnectivityWait();
        // Safety net: always restore screen timeout in case the app dies before onResume
        try {
            android.provider.Settings.System.putInt(
                getContentResolver(),
                android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
                120000);
        } catch (Throwable t) { /* ignore */ }
        try {
            if (alarmReceiver != null) {
                unregisterReceiver(alarmReceiver);
                alarmReceiver = null;
            }
        } catch (Throwable t) {
            Log.w(TAG, "onDestroy unregisterReceiver: " + t);
        }
        super.onDestroy();
    }

    /** Electric-Sign-style: keep screen on and show when locked, or allow sleep.
     * When awake=false we clear FLAG_KEEP_SCREEN_ON so the device can blank and show the NOOK screensaver
     * (the image we write to ApiPrefs.getScreensaverPath()). */
    private void setKeepScreenAwake(boolean awake) {
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        Window win = getWindow();
        if (awake) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                pm.userActivity(SystemClock.uptimeMillis(), false);
            }
            win.addFlags(flags);
        } else {
            win.clearFlags(flags);
        }
    }

    /** Schedule alarm to wake and trigger next fetch at (now + millis). */
    /** Schedule the next fetch cycle based on allow-sleep setting. */
    private void scheduleNextCycle() {
        if (ApiPrefs.isAllowSleep(this)) {
            scheduleScreensaverThenSleep();
        } else {
            scheduleRefresh();
        }
    }

    private long scheduleReload(long millis) {
        if (alarmManager == null || alarmPendingIntent == null) return 0;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis() + millis);
        long wakeTime = cal.getTimeInMillis();
        alarmManager.set(AlarmManager.RTC_WAKEUP, wakeTime, alarmPendingIntent);
        return wakeTime;
    }

    /** After SCREENSAVER_DELAY_MS (5s), put device in sleep-ready state (clear keep-screen-on, WiFi off, alarm set).
     * We do NOT show generic in-app — the API image stays on screen. If "write screensaver" is on we write
     * the displayed API image to the NOOK screensaver path so the device shows it when it sleeps (e.g. after 2m). */
    private void scheduleScreensaverThenSleep() {
        if (pendingSleepRunnable != null) {
            refreshHandler.removeCallbacks(pendingSleepRunnable);
        }
        pendingSleepRunnable = new Runnable() {
            @Override
            public void run() {
                pendingSleepRunnable = null;
                if (!ApiPrefs.isAllowSleep(DisplayActivity.this)) return;
                // Write screensaver so NOOK shows our image while asleep
                if (lastDisplayedImage != null) {
                    writeScreenshotToScreensaver(lastDisplayedImage);
                } else {
                    writeGenericScreensaver();
                }
                long sleepMs = refreshMs - SCREENSAVER_DELAY_MS;
                if (sleepMs < 0) sleepMs = 0;
                // subtract WiFi warmup so the alarm fires early enough to warm up WiFi before fetch
                sleepMs = Math.max(0, sleepMs - WIFI_WARMUP_MS);
                scheduleReload(sleepMs);
                setKeepScreenAwake(false);
                if (ApiPrefs.isAutoDisableWifi(DisplayActivity.this)) {
                    WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                    if (wifi != null) wifi.setWifiEnabled(false);
                }
                logD("sleep-ready: alarm in " + (sleepMs / 1000L) + "s (+15s warmup = next image on time; NOOK may blank after idle, e.g. 2m)");
            }
        };
        refreshHandler.postDelayed(pendingSleepRunnable, SCREENSAVER_DELAY_MS);
        logD("sleep-ready in " + (SCREENSAVER_DELAY_MS / 1000L) + "s (API image stays; NOOK shows screensaver when it sleeps)");
    }

    /** Show bundled generic image (res/drawable-mdpi/generic_display.jpg) and, if allow-sleep, write it as screensaver and go to sleep. */
    private void showGenericImageAndSleep() {
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.generic_display);
        } catch (Throwable t) {
            logW("generic_display decode failed: " + t);
        }
        if (bitmap == null) {
            logW("generic_display not found or failed to load");
            return;
        }
        imageView.setImageBitmap(bitmap);
        imageView.setVisibility(View.VISIBLE);
        if (imageRotateLayout != null) imageRotateLayout.setVisibility(View.VISIBLE);
        if (contentScroll != null) contentScroll.setVisibility(View.GONE);
        if (logView != null) logView.setVisibility(View.GONE);
        forceFullRefresh();
        logD("displayed generic image");
        logD("next display in " + (refreshMs / 1000L) + "s");
        if (ApiPrefs.isAllowSleep(this)) {
            writeScreenshotToScreensaver(bitmap);
            scheduleReload(Math.max(0, refreshMs - WIFI_WARMUP_MS));
            setKeepScreenAwake(false);
            if (ApiPrefs.isAutoDisableWifi(this)) {
                WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) wifi.setWifiEnabled(false);
            }
            logD("sleep allowed: alarm set, screen off, wifi off");
        } else {
            scheduleRefresh();
        }
    }

    private static boolean isConnectedToNetwork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifi != null && wifi.isConnected()) return true;
        NetworkInfo mobile = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        return mobile != null && mobile.isConnected();
    }

    /**
     * Returns true if it is safe to attempt a fetch immediately:
     * - ConnectivityManager reports connected (normal case), OR
     * - WiFi is enabled and already has an IP address (handles the Android 2.1
     *   race where isConnected() lags behind actual DHCP/association state).
     *
     * Only returns false if WiFi is off or has no IP — i.e. we genuinely need
     * to wait before the network is usable.
     */
    private static boolean isWifiReadyOrConnected(Context context) {
        if (isConnectedToNetwork(context)) return true;
        WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wm == null || !wm.isWifiEnabled()) return false;
        WifiInfo info = wm.getConnectionInfo();
        return info != null && info.getIpAddress() != 0;
    }

    /** Write bundled generic_display.jpg to screensaver path. Used as fallback when no API image has been displayed yet. */
    private void writeGenericScreensaver() {
        Bitmap b = null;
        try {
            b = BitmapFactory.decodeResource(getResources(), R.drawable.generic_display);
        } catch (Throwable t) {
            logW("generic_display for screensaver: " + t);
        }
        if (b != null) writeScreenshotToScreensaver(b);
    }

    /** Write given bitmap to screensaver path so NOOK shows it while asleep. */
    private void writeScreenshotToScreensaver(Bitmap bitmap) {
        if (bitmap == null) return;
        String path = ApiPrefs.getScreensaverPath();
        if (path == null || path.length() == 0) return;
        String dirPath = path;
        int lastSlash = dirPath.lastIndexOf('/');
        if (lastSlash >= 0) dirPath = dirPath.substring(0, lastSlash);
        try {
            File dir = new File(dirPath);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    logW("screensaver mkdir failed (skipping write): " + dirPath);
                    return;
                }
            }
        } catch (Throwable t) {
            logW("screensaver mkdir: " + t);
        }
        try {
            FileOutputStream out = new FileOutputStream(new File(path));
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            logD("screensaver written: " + path);
        } catch (Throwable t) {
            logW("screensaver write failed: " + path + " — " + t
                    + " (dir exists=" + new File(dirPath).exists() + ")");
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
        // Cancel any pending sleep runnable to prevent WiFi being turned off mid-fetch
        if (pendingSleepRunnable != null) {
            refreshHandler.removeCallbacks(pendingSleepRunnable);
            pendingSleepRunnable = null;
        }
        // Only wait for WiFi if it's actually off or has no IP yet.
        // If WiFi is enabled and has an IP, proceed directly — isConnectedToNetwork()
        // can return false momentarily on Android 2.1 (DHCP state lag) even when the
        // radio is fully associated, which would incorrectly send us into the 30s
        // connectivity wait and trigger "Couldn't connect" for no real reason.
        if (!isWifiReadyOrConnected(this)) {
            WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && !wifi.isWifiEnabled()) {
                wifi.setWifiEnabled(true);
            }
            waitForWifiThenFetch();
            return;
        } else if (!isConnectedToNetwork(this)) {
            logD("wifi has IP but ConnectivityManager not yet connected — proceeding anyway");
        }
        if (!ensureCredentials()) {
            return;
        }
        if (fetchInProgress) {
            return;
        }
        cancelWifiRecovery();
        wifiRecoveryAttempts = 0;
        fetchInProgress = true;
        fetchStartedFromMenu = menuVisible;
        // Silent background fetch when aggressive sleep is on, or when running as a
        // showcase cell — keep the current image visible until the new one arrives.
        boolean silentFetch = (ApiPrefs.isSuperSleep(this) && ApiPrefs.isAllowSleep(this) && !menuVisible)
                || (isShowcaseCell() && !menuVisible);
        if (!silentFetch) {
            setBootStatus("Fetching...");
            appendLogLine("Fetching...");
        }
        // Only show Loading in the dialog when user tapped Next. Resume/alarm wake: keep previous display, fetch in background.
        if (menuVisible) {
            showMenuStatus("Loading...", false);
        }
        String httpsUrl = resolveApiBaseUrl() + API_DISPLAY_PATH;
        logD("fetch reason=" + fetchReason + " wifi=" + getWifiStateString());
        logD("start: " + httpsUrl);
        ApiFetchTask.start(this, httpsUrl, resolveApiId(), resolveApiToken());
    }

    private String getWifiStateString() {
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifi == null) return "null";
        if (!wifi.isWifiEnabled()) return "off";
        WifiInfo info = wifi.getConnectionInfo();
        if (info == null) return "on/no-info";
        int ip = info.getIpAddress();
        if (ip == 0) return "on/no-ip";
        return "connected";
    }

    /** Show "Loading..." in content area and hide log so the dialog is clean. */
    private void showLoadingMessage() {
        if (contentView != null) contentView.setText("Loading...");
        if (contentScroll != null) contentScroll.setVisibility(View.VISIBLE);
        if (imageView != null) imageView.setVisibility(View.GONE);
        if (logView != null) logView.setVisibility(View.GONE);
        // Skip EPD refresh — this is transient, image replaces it immediately
    }

    private void scheduleRefresh() {
        if (ApiPrefs.isAllowSleep(this)) {
            return;
        }
        if (refreshRunnable == null) {
            refreshRunnable = new Runnable() {
                public void run() {
                    fetchReason = "timer";
                    startFetch();
                    refreshHandler.postDelayed(this, refreshMs);
                }
            };
        }
        refreshHandler.removeCallbacks(refreshRunnable);
        long wakeAt = System.currentTimeMillis() + refreshMs;
        logD("next display in " + (refreshMs / 1000L) + "s (at " + new java.util.Date(wakeAt) + ")");
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
            logView.setText(logBuffer.toString());
        }
    }
    
    /** Hide boot screen and show normal content */

    /** Hide the boot header layout even after bootComplete (used when UI overlaps). */
    private void hideBootLayout() {
        if (bootLayout != null) bootLayout.setVisibility(View.GONE);
    }
    private void hideBootScreen() {
        if (bootComplete) return;
        bootComplete = true;
        if (bootLayout != null) bootLayout.setVisibility(View.GONE);
        hideNoWifiOverlay();
    }
    
    private void setBootStatus(String status) {
        if (bootStatus != null && !bootComplete) {
            bootStatus.setText("TRMNL  " + status);
        }
    }

    private void showGiftModeScreen() {

        hideBootScreen();
        if (bootLayout != null) bootLayout.setVisibility(View.GONE);
        if (logView != null) logView.setVisibility(View.GONE);
        if (imageRotateLayout != null) imageRotateLayout.setVisibility(View.GONE);
        if (contentScroll != null) contentScroll.setVisibility(View.VISIBLE);
        
        String code = ApiPrefs.getFriendlyDeviceCode(this);
        String fromName = ApiPrefs.getGiftFromName(this);
        String toName = ApiPrefs.getGiftToName(this);
        
        // Build a left-aligned layout
        ScrollView.LayoutParams scrollParams = new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT);
        
        LinearLayout giftLayout = new LinearLayout(this);
        giftLayout.setOrientation(LinearLayout.VERTICAL);
        giftLayout.setPadding(50, 75, 50, 40);
        giftLayout.setLayoutParams(scrollParams);
        
        // Logo + Title row
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        headerRow.addView(logo);
        
        TextView title = new TextView(this);
        title.setText("TRMNL");
        title.setTextSize(26);
        title.setTextColor(0xFF000000);
        title.setPadding(12, 0, 0, 0);
        headerRow.addView(title);
        
        giftLayout.addView(headerRow);
        
        // Greeting
        StringBuilder greeting = new StringBuilder();
        if (toName != null && toName.length() > 0) {
            greeting.append("Hey ").append(toName).append("! ");
        }
        if (fromName != null && fromName.length() > 0) {
            greeting.append(fromName).append(" gifted you this display!");
        } else {
            greeting.append("This display was gifted to you!");
        }
        
        TextView greetingView = new TextView(this);
        greetingView.setText(greeting.toString());
        greetingView.setTextSize(18);
        greetingView.setTextColor(0xFF000000);
        LinearLayout.LayoutParams greetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        greetParams.topMargin = 16;
        giftLayout.addView(greetingView, greetParams);
        
        // Description
        TextView desc = new TextView(this);
        desc.setText("Customize it with your calendar, to-do list, weather, news, and hundreds of other plugins at trmnl.com — all your data, on your display.");
        desc.setTextSize(14);
        desc.setTextColor(0xFF666666);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = 6;
        giftLayout.addView(desc, descParams);
        
        // Divider line
        View divider = new View(this);
        divider.setBackgroundColor(0xFFCCCCCC);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, 1);
        divParams.topMargin = 20;
        giftLayout.addView(divider, divParams);
        
        // Setup steps
        boolean webSetup = ApiPrefs.isGiftWebSetup(this);
        
        boolean hasCode = code != null && code.length() > 0;
        if (webSetup) {
            // Web setup: show URL as primary CTA
            TextView stepsTitle = new TextView(this);
            stepsTitle.setText("SETUP");
            stepsTitle.setTextSize(13);
            stepsTitle.setTextColor(0xFF888888);
            LinearLayout.LayoutParams stepsTitleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stepsTitleParams.topMargin = 20;
            giftLayout.addView(stepsTitle, stepsTitleParams);
            
            // Primary CTA: web URL
            TextView urlLabel = new TextView(this);
            urlLabel.setText("Visit this URL on your computer:");
            urlLabel.setTextSize(15);
            urlLabel.setTextColor(0xFF000000);
            LinearLayout.LayoutParams urlLabelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            urlLabelParams.topMargin = 16;
            giftLayout.addView(urlLabel, urlLabelParams);
            
            TextView urlView = new TextView(this);
            if (hasCode) {
                urlView.setText("https://nooks.bpmct.net/?device=" + code);
            } else {
                urlView.setText("https://nooks.bpmct.net");
            }
            urlView.setTextSize(20);
            urlView.setTextColor(0xFF000000);
            urlView.setBackgroundColor(0xFFEEEEEE);
            urlView.setPadding(16, 12, 16, 12);
            LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            urlParams.topMargin = 8;
            giftLayout.addView(urlView, urlParams);
        } else {
            // Manual setup steps (original flow)
            TextView stepsTitle = new TextView(this);
            stepsTitle.setText("SETUP");
            stepsTitle.setTextSize(13);
            stepsTitle.setTextColor(0xFF888888);
            LinearLayout.LayoutParams stepsTitleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stepsTitleParams.topMargin = 20;
            giftLayout.addView(stepsTitle, stepsTitleParams);
            
            // Step 1
            giftLayout.addView(createStepRow("1", "Sign up at trmnl.com/signup"), createStepParams(14));
            
            // Step 2
            String step2Text = (code != null && code.length() > 0) 
                    ? "Add device with code: " + code 
                    : "Add device (get code from gifter)";
            giftLayout.addView(createStepRow("2", step2Text), createStepParams(10));
            
            // Step 3
            giftLayout.addView(createStepRow("3", "Tap screen → Settings → Edit"), createStepParams(10));
        }
        
        // Replace contentView's parent contents
        if (contentScroll != null) {
            contentScroll.removeAllViews();
            contentScroll.setFillViewport(true);
            contentScroll.addView(giftLayout);
        }
        
        // Write gift mode screen to screensaver so NOOK shows it when asleep
        writeGiftModeScreensaver(code, fromName, toName);

        // Allow device to sleep; user tap wakes it
        if (ApiPrefs.isAllowSleep(this)) {
            setKeepScreenAwake(false);

            logD("gift mode: sleep-ready (tap to wake)");
        }
    }
    
    private LinearLayout createStepRow(String number, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView numView = new TextView(this);
        numView.setText(number);
        numView.setTextSize(18);
        numView.setTextColor(0xFF000000);
        numView.setGravity(Gravity.CENTER);
        numView.setBackgroundColor(0xFFEEEEEE);
        numView.setPadding(14, 6, 14, 6);
        row.addView(numView);
        
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(14);
        textView.setTextColor(0xFF000000);
        textView.setPadding(12, 0, 0, 0);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(textView);
        
        return row;
    }
    
    private LinearLayout.LayoutParams createStepParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }
    
    private void writeGiftModeScreensaver(String code, String fromName, String toName) {
        // Both custom and fallback paths ultimately write to display.png
        // (via writeScreenshotToScreensaver) — this is always the final
        // screensaver location the NOOK reads from when the device sleeps.
        // Check for custom gift screensaver image path first
        String customPath = ApiPrefs.getCustomGiftScreensaverPath(this);
        if (customPath != null && customPath.length() > 0) {
            java.io.File customFile = new java.io.File(customPath);
            if (customFile.exists()) {
                Bitmap custom = BitmapFactory.decodeFile(customPath);
                if (custom != null) {
                    logD("Using custom gift screensaver: " + customPath);
                    writeScreenshotToScreensaver(custom);
                    return;
                } else {
                    logW("Could not decode custom gift screensaver: " + customPath);
                }
            } else {
                logW("Custom gift screensaver not found: " + customPath);
            }
        }
        // Fallback: use bundled gift screensaver image (native portrait 600x800)
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.gift_screensaver);
        if (bitmap != null) {
            writeScreenshotToScreensaver(bitmap);
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
        showMenuNormal();
        updateMenuBattery();
        if (nextButton != null) {
            nextButton.setPressed(false);
            nextButton.refreshDrawableState();
            if (ApiPrefs.isGiftModeEnabled(this)) {
                // In gift mode: Gallery goes back to showcase, Customize Yourself opens gift setup
                nextButton.setText("Gallery");
                nextButton.setOnTouchListener(new View.OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            hideMenu();
                            startActivity(new Intent(DisplayActivity.this, ShowcaseActivity.class));
                            finish();
                            return true;
                        }
                        return false;
                    }
                });
            } else {
                nextButton.setText("Next");
                nextButton.setOnTouchListener(new View.OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            logD("menu: next tapped");
                            if (USE_GENERIC_IMAGE) {
                                hideMenu();
                                showGenericImageAndSleep();
                            } else {
                                fetchReason = "menu-next";
                                showMenuStatus("Loading...", false);
                                startFetch();
                            }
                            return true;
                        }
                        return false;
                    }
                });
            }
        }
        if (customizeButton != null) {
            if (ApiPrefs.isGiftModeEnabled(this)) {
                customizeButton.setPressed(false);
                customizeButton.refreshDrawableState();
                customizeButton.setVisibility(giftScreenVisible ? View.GONE : View.VISIBLE);
            } else {
                customizeButton.setVisibility(View.GONE);
            }
        }
        applyMenuLayoutParams();
        if (menuLayout != null) menuLayout.setVisibility(View.VISIBLE);
        if (menuScrim != null) menuScrim.setVisibility(View.VISIBLE);
        forceFullRefresh();
    }

    /** Show status text in the dialog (Loading/Connecting/Error); optionally show Next for retry. Keeps image visible. */
    private void showMenuStatus(String msg, boolean showNextButton) {
        // Hide boot screen when showing menu status
        hideBootLayout();
        if (loadingStatusView != null) {
            loadingStatusView.setText(msg);
            loadingStatusView.setVisibility(View.VISIBLE);
        }
        if (batteryView != null) batteryView.setVisibility(View.GONE);
        if (nextButton != null) nextButton.setVisibility(showNextButton ? View.VISIBLE : View.GONE);
        if (settingsButton != null) settingsButton.setVisibility(View.GONE);
        if (customizeButton != null) customizeButton.setVisibility(View.GONE);
        if (menuLayout != null && menuScrim != null) {
            applyMenuLayoutParams();
            menuLayout.setVisibility(View.VISIBLE);
            menuScrim.setVisibility(View.VISIBLE);
        }
        menuVisible = true;
        forceFullRefresh();
    }


    /** Restore dialog to Battery / Next / Settings / Sleep. */
    private void applyMenuLayoutParams() {
        if (menuLayout == null) return;
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                480,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        menuLayout.setLayoutParams(p);
        logD("applyMenuLayoutParams: set 480 caller=" + Thread.currentThread().getStackTrace()[3].getMethodName());
    }

    private void showMenuNormal() {
        if (loadingStatusView != null) loadingStatusView.setVisibility(View.GONE);
        if (batteryView != null) batteryView.setVisibility(View.VISIBLE);
        if (nextButton != null) nextButton.setVisibility(View.VISIBLE);
        if (settingsButton != null) settingsButton.setVisibility(View.VISIBLE);
        // No forceFullRefresh here — showMenuStatus callers handle their own refresh
        if (menuLayout != null) {
            logD("menu measured w=" + menuLayout.getMeasuredWidth()
                    + " h=" + menuLayout.getMeasuredHeight()
                    + " w=" + menuLayout.getWidth()
                    + " h=" + menuLayout.getHeight()
                    + " left=" + menuLayout.getLeft()
                    + " top=" + menuLayout.getTop()
                    + " root.w=" + ((android.view.View)menuLayout.getParent()).getWidth()
                    + " root.h=" + ((android.view.View)menuLayout.getParent()).getHeight());
        }
    }

    private void hideMenu() {
        menuVisible = false;
        if (menuLayout != null) menuLayout.setVisibility(View.GONE);
        if (menuScrim != null) menuScrim.setVisibility(View.GONE);
        flashEinkTransition();
    }

    /**
     * Removes the no-WiFi overlay if it is currently shown.
     */
    private void hideNoWifiOverlay() {
        if (noWifiOverlay != null) {
            if (noWifiOverlay.getParent() != null) {
                ((ViewGroup) noWifiOverlay.getParent()).removeView(noWifiOverlay);
            }
            noWifiOverlay = null;
        }
    }

    private void showNoWifiScreen() {
        // Gift mode without showcase: static screen, no WiFi needed, skip overlay.
        if (ApiPrefs.isGiftModeEnabled(this) && !ApiPrefs.isShowcaseModeEnabled(this)) return;
        // Don't stack duplicates.
        if (noWifiOverlay != null) return;

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(40, 60, 40, 40);
        overlay.setBackgroundColor(0xFFFFFFFF);
        // Consume all touches so nothing below the overlay fires.
        overlay.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) { return true; }
        });

        TextView msg = new TextView(this);
        msg.setText("This smart display needs WiFi.");
        msg.setTextSize(18);
        msg.setTextColor(0xFF000000);
        msg.setGravity(Gravity.CENTER);
        overlay.addView(msg, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView sub = new TextView(this);
        sub.setText("Once connected, the screen will update automatically.");
        sub.setTextSize(13);
        sub.setTextColor(0xFF555555);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = 12;
        overlay.addView(sub, subParams);

        Button wifiBtn = new Button(this);
        wifiBtn.setText("Wi-Fi Settings");
        wifiBtn.setTextColor(0xFF000000);
        wifiBtn.setBackgroundColor(0xFFDDDDDD);
        wifiBtn.setPadding(20, 16, 20, 16);
        wifiBtn.setFocusable(true);
        wifiBtn.setClickable(true);
        wifiBtn.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    new AlertDialog.Builder(DisplayActivity.this)
                        .setTitle("Opening Wi-Fi Settings")
                        .setMessage("After connecting, press the Home button on the bottom of this device to return to this app.")
                        .setPositiveButton("Open Wi-Fi Settings", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Intent wifiIntent = new Intent();
                                wifiIntent.setClassName("com.android.settings", "com.android.settings.wifi.Settings_Wifi_Settings");
                                try {
                                    startActivity(wifiIntent);
                                } catch (Throwable t2) {
                                    try { startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)); } catch (Throwable t3) {}
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                }
                return true;
            }
        });
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = 24;
        overlay.addView(wifiBtn, btnParams);

        Button retryBtn = new Button(this);
        retryBtn.setText("Retry");
        retryBtn.setTextColor(0xFF000000);
        retryBtn.setBackgroundColor(0xFFDDDDDD);
        retryBtn.setPadding(20, 16, 20, 16);
        retryBtn.setFocusable(true);
        retryBtn.setClickable(true);
        retryBtn.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    hideNoWifiOverlay();
                    fetchReason = "retry";
                    waitForWifiThenFetch();
                }
                return true;
            }
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = 8;
        overlay.addView(retryBtn, retryParams);

        if (ApiPrefs.isGiftWebSetup(this)) {
            LinearLayout.LayoutParams usbParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            usbParams.topMargin = 16;

            TextView usbLine1 = new TextView(this);
            usbLine1.setText("If the touchscreen isn't working or the password is complex,");
            usbLine1.setTextSize(13);
            usbLine1.setTextColor(0xFF888888);
            usbLine1.setGravity(Gravity.CENTER);
            overlay.addView(usbLine1, usbParams);

            TextView usbLine2 = new TextView(this);
            String url = "visit nooks.bpmct.net on a computer connected via USB.";
            SpannableString urlSpan = new SpannableString(url);
            int start = url.indexOf("nooks.bpmct.net");
            urlSpan.setSpan(new UnderlineSpan(), start, start + "nooks.bpmct.net".length(), 0);
            usbLine2.setText(urlSpan);
            usbLine2.setTextSize(13);
            usbLine2.setTextColor(0xFF888888);
            usbLine2.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams usbParams2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            usbParams2.topMargin = 2;
            overlay.addView(usbLine2, usbParams2);
        }

        noWifiOverlay = overlay;
        if (showcaseStatusView != null) showcaseStatusView.setVisibility(View.GONE);
        if (rootLayout != null) {
            rootLayout.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT,
                    ViewGroup.LayoutParams.FILL_PARENT));
        }
        forceFullRefresh();
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
        // Trigger EPD refresh AFTER content is in framebuffer (100ms delay matches upstream)
        root.postDelayed(new Runnable() {
            public void run() {
                View r = getWindow().getDecorView();
                if (r != null) r.invalidate();
                triggerEpdRefresh();
            }
        }, 100);
    }
    
    /** Trigger NOOK Simple Touch hardware e-ink refresh via sysfs. */
    private void triggerEpdRefresh() {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(
                "/sys/devices/platform/omap3epfb.0/graphics/fb0/epd_refresh");
            fw.write("1");
            fw.close();
        } catch (Exception e) {
            // Silently fail - fall back to Android refresh
        }
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
        // Second flush 120ms later — ensures e-ink clears ghost from menu overlay.
        refreshHandler.postDelayed(new Runnable() {
            public void run() {
                forceFullRefresh();
            }
        }, 120);
    }

    /**
     * Immediately put the device into sleep-ready state: cancel any pending sleep/refresh
     * runnables, write the current screensaver image, schedule the next alarm, clear
     * FLAG_KEEP_SCREEN_ON (so the NOOK can blank), and optionally turn WiFi off.
     *
     * This is safe to call regardless of the allow_sleep setting — the Sleep button
     * does an immediate sleep rather than going through scheduleScreensaverThenSleep()
     * so FLAG_KEEP_SCREEN_ON never blocks us.
     */
    private void sleepNow() {
        logD("sleepNow: initiating manual sleep");

        // Cancel any existing scheduled sleep / refresh so they don't race
        if (pendingSleepRunnable != null) {
            refreshHandler.removeCallbacks(pendingSleepRunnable);
            pendingSleepRunnable = null;
            logD("sleepNow: cancelled pending sleepRunnable");
        }
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
            logD("sleepNow: cancelled pending refreshRunnable");
        }
        cancelConnectivityWait();

        // Write the current API image (or generic fallback) as screensaver so the
        // NOOK shows something while asleep.
        if (lastDisplayedImage != null) {
            logD("sleepNow: writing last displayed image as screensaver");
            writeScreenshotToScreensaver(lastDisplayedImage);
        } else {
            logD("sleepNow: no API image yet, writing generic screensaver");
            writeGenericScreensaver();
        }

        // Schedule wake alarm so the next refresh fires on time.
        // Use the current refreshMs minus the standard screensaver delay that was
        // already skipped (we slept early), and minus the WiFi warmup, so the
        // alarm fires early enough for WiFi to reconnect before the fetch.
        long sleepMs = refreshMs - SCREENSAVER_DELAY_MS - WIFI_WARMUP_MS;
        if (sleepMs < 0) sleepMs = 0;
        long wakeTime = scheduleReload(sleepMs);
        logD("sleepNow: alarm scheduled in " + (sleepMs / 1000L) + "s (wake at " + wakeTime + ")");


        // Turn WiFi off to save power when auto-disable is on — but never for showcase
        // cells, which need WiFi to stay on for the next cell fetch.
        if (!isShowcaseCell() && ApiPrefs.isAutoDisableWifi(this)) {
            WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && wifi.isWifiEnabled()) {
                wifi.setWifiEnabled(false);
                logD("sleepNow: WiFi disabled");
            }
        } else {
            logD("sleepNow: leaving WiFi on (showcase cell or auto-disable off)");
        }

        // Clear FLAG_KEEP_SCREEN_ON so the window manager stops keeping the screen on.
        logD("sleepNow: clearing keep-screen-awake flag");
        setKeepScreenAwake(false);

        // Delay KEYCODE_POWER by 5s — gives the menu time to dismiss and the
        // user's finger time to lift. sleepPending blocks onResume from
        // re-asserting FLAG_KEEP_SCREEN_ON during this window.
        sleepPending = true;
        if (pendingScreenOffRunnable != null) {
            refreshHandler.removeCallbacks(pendingScreenOffRunnable);
        }
        pendingScreenOffRunnable = new Runnable() {
            public void run() {
                pendingScreenOffRunnable = null;
                // NOTE: sleepPending stays true until onResume() after the real wake.
                logD("sleepNow: setting screen_off_timeout=1000 to force natural sleep");
                try {
                    android.provider.Settings.System.putInt(
                        getContentResolver(),
                        android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
                        1000);
                    logD("sleepNow: screen_off_timeout set to 1s");
                } catch (Throwable t) {
                    logW("sleepNow: could not set timeout: " + t);
                }
                logD("sleepNow: done");
            }
        };
        refreshHandler.postDelayed(pendingScreenOffRunnable, 2000);
        logD("sleepNow: setup complete, screen-off in 5s (sleepPending=true)");
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

    /** Returns the API ID to use — showcase override takes priority over ApiPrefs. */
    private String resolveApiId() {
        return (showcaseApiId != null) ? showcaseApiId : ApiPrefs.getApiId(this);
    }

    /** Returns the API token to use — showcase override takes priority over ApiPrefs. */
    private String resolveApiToken() {
        return (showcaseApiToken != null) ? showcaseApiToken : ApiPrefs.getApiToken(this);
    }

    /** Returns the API base URL — per-cell showcase URL takes priority over global pref. */
    private String resolveApiBaseUrl() {
        if (showcaseApiUrl != null && showcaseApiUrl.length() > 0) {
            // showcaseApiUrl already has /display appended; strip it since caller adds API_DISPLAY_PATH
            String u = showcaseApiUrl;
            if (u.endsWith("/display")) u = u.substring(0, u.length() - "/display".length());
            return u;
        }
        return ApiPrefs.getApiBaseUrl(this);
    }

    /** True when this instance was launched from a showcase cell tap. */
    private boolean isShowcaseCell() {
        return showcaseApiToken != null && showcaseApiToken.length() > 0;
    }

    private void applyShowcaseExtras(Intent intent) {
        if (intent == null) return;
        String id    = intent.getStringExtra(EXTRA_SHOWCASE_API_ID);
        String token = intent.getStringExtra(EXTRA_SHOWCASE_API_TOKEN);
        String url   = intent.getStringExtra(EXTRA_SHOWCASE_API_URL);
        int    cell  = intent.getIntExtra(EXTRA_SHOWCASE_CELL, EXTRA_SHOWCASE_CELL_NONE);
        if (token != null && token.length() > 0) {
            showcaseApiId    = (id != null) ? id : "";
            showcaseApiToken = token;
            showcaseApiUrl   = (url != null) ? url : "";
            showcaseCell     = cell;
        }
    }

    /**
     * Show a pre-loaded bitmap immediately (same logic as onPostExecute success path)
     * then hand off to the normal sleep/cycle machinery.
     */
    private void displayPreloadedImage(Bitmap bmp) {
        hideBootScreen();
        imageView.setImageBitmap(bmp);
        lastDisplayedImage = bmp;
        writeScreenshotToScreensaver(bmp);
        imageView.setVisibility(View.VISIBLE);
        if (imageRotateLayout != null) imageRotateLayout.setVisibility(View.VISIBLE);
        if (contentScroll != null) contentScroll.setVisibility(View.GONE);
        if (logView != null) logView.setVisibility(View.GONE);
        hideMenu();
        logD("displayed preloaded showcase image");
        // Show cached image immediately, then fetch fresh in background.
        // Sleep (if enabled) happens after the fresh fetch completes in onPostExecute.
        forceFullRefresh();
        if (isShowcaseCell() && ApiPrefs.isSuperSleep(this) && ApiPrefs.isAllowSleep(this)) {
            if (showcaseStatusView != null) showcaseStatusView.setVisibility(View.VISIBLE);
        }
        fetchReason = "preload-refresh";
        startFetch();
    }

    private void goBackToShowcase() {
        hideMenu();
        startActivity(new Intent(DisplayActivity.this, ShowcaseActivity.class));
        finish();
    }

    private boolean ensureCredentials() {
        if (isShowcaseCell()) return true;
        // Don't redirect to settings if gift mode is enabled
        if (ApiPrefs.isGiftModeEnabled(this)) {
            return false;
        }
        if (!ApiPrefs.hasCredentials(this)) {
            startActivity(new Intent(this, SettingsActivity.class));
            return false;
        }
        return true;
    }

    private void logD(final String msg) {
        Log.d(TAG, msg);
        FileLogger.d(TAG, msg);
        if (!bootComplete) logToScreen(msg);
    }

    private void logW(final String msg) {
        Log.w(TAG, msg);
        FileLogger.w(TAG, msg);
        if (!bootComplete) logToScreen("W " + msg);
    }
    
    private void logToScreen(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() { appendLogLine(msg); }
        });
    }

    private void logE(final String msg, final Throwable t) {
        Log.e(TAG, msg, t);
        FileLogger.e(TAG, msg, t);
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
            int batteryPercent = getBatteryPercent(a != null ? a : null);
            int rssi = getWifiRssi(a != null ? a : null);
            if (a != null && batteryPercent >= 0) a.logD("Percent-Charged: " + batteryPercent);
            if (a != null && rssi != -999) a.logD("rssi: " + rssi);
            
            // Try BouncyCastle TLS first (supports TLS 1.2)
            if (BouncyCastleHttpClient.isAvailable()) {
                if (a != null) a.logD("trying BouncyCastle TLS 1.2");
                Hashtable headers = buildApiHeaders(apiId, apiToken, batteryPercent, rssi);
                
                // Try up to 2 times with 3s backoff
                String bcResult = null;
                for (int attempt = 1; attempt <= 2; attempt++) {
                    if (attempt > 1) {
                        if (a != null) a.logW("Attempt " + (attempt-1) + " failed: " + bcResult + " - retrying in 5s");
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                        if (a != null) a.logD("Retrying fetch...");
                    }
                    bcResult = BouncyCastleHttpClient.getHttps(
                            a != null ? a.getApplicationContext() : null,
                            httpsUrl,
                            headers);
                    if (bcResult != null && !bcResult.startsWith("Error:")) {
                        ApiResult parsed = null;
                        if (a != null) {
                            final DisplayActivity aFinal = a;
                            TrmnlApiResponseParser.Result r = TrmnlApiResponseParser.parseAndMaybeFetchImage(
                                    aFinal.getApplicationContext(),
                                    bcResult,
                                    new TrmnlApiResponseParser.Logger() {
                                        public void logD(String msg) { aFinal.logD(msg); }
                                        public void logW(String msg) { aFinal.logW(msg); }
                                    });
                            if (r != null && r.showImage && r.bitmap != null) {
                                if (r.refreshRateSeconds > 0) {
                                    a.updateRefreshRateSeconds(r.refreshRateSeconds);
                                }
                                parsed = new ApiResult(r.rawText, r.imageUrl, r.bitmap);
                            } else {
                                // Preserve previous behavior: still allow refresh rate update even if no image
                                if (r != null && r.refreshRateSeconds > 0) {
                                    a.updateRefreshRateSeconds(r.refreshRateSeconds);
                                }
                                parsed = new ApiResult(bcResult);
                            }
                        }
                        return (parsed != null) ? parsed : new ApiResult(bcResult);
                    }
                }
                if (a != null) a.logW("All attempts failed: " + bcResult);
                return bcResult;
            }

            String error = "Error: TLS 1.2 client unavailable (BouncyCastle required)";
            if (a != null) a.logW(error);
            return error;
        }
        
        private Object fetchUrl(String url, boolean isHttps, String apiId, String apiToken,
                                int batteryPercent, int rssi) {
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
                if (batteryPercent >= 0) {
                    conn.setRequestProperty("Percent-Charged", String.valueOf(batteryPercent));
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
            final boolean fromMenu = a.fetchStartedFromMenu;
            a.fetchStartedFromMenu = false;
            if (result instanceof ApiResult) {
                ApiResult ar = (ApiResult) result;
                if (ar.showImage && ar.bitmap != null) {
                    if (ar.rawText != null) {
                        a.logD("response body:\n" + ar.rawText);
                    }
                    a.hideBootScreen();
                    a.hideNoWifiOverlay();
                    if (a.bootLayout != null) a.bootLayout.setVisibility(View.GONE);
                    a.imageView.setImageBitmap(ar.bitmap);
                    a.lastDisplayedImage = ar.bitmap;
                    // Always write screensaver immediately so TRMNL appears in NOOK's screensaver list
                    a.writeScreenshotToScreensaver(ar.bitmap);
                    // If this is a showcase cell, update the grid cache
                    if (a.isShowcaseCell()) {
                        ShowcaseActivity.saveCachedBitmap(a, a.showcaseCell, ar.bitmap);
                    }
                    boolean superSleep = ApiPrefs.isSuperSleep(a);
                    boolean allowSleep = ApiPrefs.isAllowSleep(a);
                    a.logD("super-sleep check: superSleep=" + superSleep + " allowSleep=" + allowSleep + " fromMenu=" + fromMenu);
                    // Always render the image in-app — hides the boot/log UI
                    // and ensures errors still surface via the normal error path.
                    a.imageView.setVisibility(View.VISIBLE);
                    if (a.imageRotateLayout != null) a.imageRotateLayout.setVisibility(View.VISIBLE);
                    if (a.contentScroll != null) a.contentScroll.setVisibility(View.GONE);
                    if (a.logView != null) a.logView.setVisibility(View.GONE);
                    a.hideMenu();
                    if (ar.imageUrl != null) a.logD("image url: " + ar.imageUrl);
                    a.logD("displayed image");
                    if (superSleep && allowSleep && !fromMenu) {
                        // Aggressive sleep: hide status bar, flush EPD, then sleep.
                        if (a.showcaseStatusView != null) a.showcaseStatusView.setVisibility(View.GONE);
                        a.forceFullRefresh();
                        a.logD("super sleep: sleeping after image render");
                        a.sleepNow();
                    } else {
                        a.logD("next display in " + (a.refreshMs / 1000L) + "s");
                        a.forceFullRefresh();
                        a.scheduleNextCycle();
                    }
                    int pct = getBatteryPercent(a);
                    if (pct >= 0) a.logD("Percent-Charged: " + pct);
                    int rssi = getWifiRssi(a);
                    if (rssi != -999) a.logD("rssi: " + rssi);
                    return;
                }

                // Got API response but no image - show error and schedule retry
                String text = ar.rawText != null ? ar.rawText : "Error: null result";
                a.logD("response body:\n" + text);
                a.logD("no image in response, will retry");
                if (a.isShowcaseCell()) {
                    // Showcase cell: keep current image, retry silently
                    if (a.showcaseStatusView != null) a.showcaseStatusView.setVisibility(View.GONE);
                } else if (fromMenu) {
                    // User tapped Next - show error in menu dialog, let them retry
                    a.showMenuStatus("No image - tap Next to retry", true);
                    a.forceFullRefresh();
                } else {
                    // Background fetch - keep current display, just schedule retry
                    a.logD("next display in " + (a.refreshMs / 1000L) + "s");
                }
                // Schedule next refresh (keep trying)
                if (ApiPrefs.isAllowSleep(a)) {
                    a.scheduleScreensaverThenSleep();
                } else {
                    a.scheduleRefresh();
                }
                return;
            }

            String text = result != null ? result.toString() : "Error: null result";
            a.hideMenu();
            a.logW("ERROR: " + text);
            if (a.isShowcaseCell()) {
                // Keep the current image visible; show error in the menu overlay so the
                // user can see it, tap to dismiss, and the next cycle will retry.
                if (a.showcaseStatusView != null) a.showcaseStatusView.setVisibility(View.GONE);
                a.showMenuStatus("Error fetching — will retry", false);
                a.forceFullRefresh();
                a.scheduleNextCycle();
                return;
            }
            a.setBootStatus("Error - tap to retry");
            if (a.bootLayout != null) a.bootLayout.setVisibility(View.VISIBLE);
            if (a.imageView != null) a.imageView.setVisibility(View.GONE);
            if (a.contentScroll != null) a.contentScroll.setVisibility(View.GONE);
            if (a.logView != null) a.logView.setVisibility(View.VISIBLE);
            a.forceFullRefresh();
            a.logD("fetch error: " + text);
            a.logD("next display in " + (a.refreshMs / 1000L) + "s");
            // Schedule next refresh even on error (keep trying)
            a.scheduleNextCycle();
            int pct = getBatteryPercent(a);
            if (pct >= 0) a.logD("Percent-Charged: " + pct);
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

    private static Hashtable buildApiHeaders(String apiId, String apiToken, int batteryPercent, int rssi) {
        Hashtable headers = new Hashtable();
        headers.put("User-Agent", "TRMNL-Nook/1.0 (Android 2.1)");
        headers.put("Accept", "application/json");
        if (apiId != null) {
            headers.put("ID", apiId);
        }
        if (apiToken != null) {
            headers.put("access-token", apiToken);
        }
        if (batteryPercent >= 0) {
            headers.put("Percent-Charged", String.valueOf(batteryPercent));
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
