package com.bpmct.trmnl_nook_simple_touch;

import android.app.Activity;
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

public class DisplayActivity extends Activity {
    public static final String EXTRA_CLEAR_IMAGE = "clear_image";
    private static final String TAG = "TRMNLAPI";
    private static final long DEFAULT_REFRESH_MS = 15 * 60 * 1000;
    private static final String API_DISPLAY_PATH = "/display";
    private static final String ALARM_REFRESH_ACTION = "com.bpmct.trmnl_nook_simple_touch.ALARM_REFRESH_ACTION";
    /** When true, skip API and show generic on screen (for testing). When false, foreground = API image, screensaver file = generic. */
    private static final boolean USE_GENERIC_IMAGE = false;
    /** Delay after showing API image before writing screensaver and going to sleep (show picture, then screensaver, then sleep full interval). */
    private static final long SCREENSAVER_DELAY_MS = 5 * 1000;
    private TextView contentView;
    private TextView logView;
    private ImageView imageView;
    private ScrollView contentScroll;
    private RotateLayout appRotateLayout;
    private FrameLayout rootLayout;
    private LinearLayout menuLayout;
    private LinearLayout bootLayout;
    private TextView bootStatus;
    private boolean bootComplete = false;
    private View menuScrim;
    private View flashOverlay;
    private TextView batteryView;
    private Button nextButton;
    private Button settingsButton;
    private TextView loadingStatusView;
    private RotateLayout imageRotateLayout;
    private boolean menuVisible = false;
    private final Handler refreshHandler = new Handler();
    private Runnable refreshRunnable;
    private volatile boolean fetchInProgress = false;
    private volatile boolean fetchStartedFromMenu = false;
    private volatile long refreshMs = DEFAULT_REFRESH_MS;
    /** Last displayed API image; used for screensaver file when allow-sleep + write-screensaver. */
    private Bitmap lastDisplayedImage;
    private final StringBuilder logBuffer = new StringBuilder();
    private static final int MAX_LOG_CHARS = 6000;
    private static final int APP_ROTATION_DEGREES = 90;

    private AlarmManager alarmManager;
    private PendingIntent alarmPendingIntent;
    private BroadcastReceiver alarmReceiver;
    private BroadcastReceiver connectivityReceiver;
    private Runnable pendingSleepRunnable;
    private Runnable pendingWifiWarmupRunnable;
    private Runnable pendingConnectivityTimeoutRunnable;
    private static final long CONNECTIVITY_MAX_WAIT_MS = 30 * 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                        showMenuStatus("Loading...", false);
                        startFetch();
                    }
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nextParams.leftMargin = 18;
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
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        settingsParams.leftMargin = 18;
        menuLayout.addView(settingsButton, settingsParams);

        loadingStatusView = new TextView(this);
        loadingStatusView.setTextColor(0xFF000000);
        loadingStatusView.setTextSize(14);
        loadingStatusView.setPadding(8, 0, 8, 0);
        loadingStatusView.setVisibility(View.GONE);
        menuLayout.addView(loadingStatusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

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
                // Electric-Sign-style: if we slept with WiFi off, turn it on and wait before fetching
                WifiManager wifi = (WifiManager) a.getSystemService(Context.WIFI_SERVICE);
                        if (ApiPrefs.isAllowSleep(a) && wifi != null && !wifi.isWifiEnabled()
                        && !isConnectedToNetwork(a)) {
                    wifi.setWifiEnabled(true);
                    a.waitForWifiThenFetch();
                    return;
                }
                startFetch();
            }
        };
        registerReceiver(alarmReceiver, new IntentFilter(ALARM_REFRESH_ACTION));

        setKeepScreenAwake(true);

        boolean wifiJustOn = ensureWifiOnWhenForeground();

        // Initial display: generic image (no API) or fetch from API
        if (USE_GENERIC_IMAGE) {
            showGenericImageAndSleep();
        } else if (ensureCredentials()) {
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
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setKeepScreenAwake(true);

        boolean wifiJustOn = ensureWifiOnWhenForeground();

        applyIntentState(getIntent());
        if (USE_GENERIC_IMAGE) {
            showGenericImageAndSleep();
        } else if (ensureCredentials()) {
            if (!fetchInProgress) {
                if (wifiJustOn) {
                    waitForWifiThenFetch();
                } else {
                    startFetch();
                }
            }
            // Don't schedule here - fetch completion will schedule the next refresh
        }
    }

    /** When we just turned WiFi on, delay fetch so connection can establish (Electric-Sign uses 45s). */
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

    /** Wait for network to come up, then start fetch. Starts as soon as connectivity appears; max wait CONNECTIVITY_MAX_WAIT_MS. */
    private void waitForWifiThenFetch() {
        cancelConnectivityWait();
        if (isConnectedToNetwork(this)) {
            startFetch();
            return;
        }
        // Only show Connecting in the dialog when user tapped Next. Resume/alarm wake: keep previous display, wait in background.
        if (menuVisible) {
            showMenuStatus("Connecting…", false);
        }
        ensureWifiOnWhenForeground();
        final DisplayActivity a = this;
        final boolean showErrorInMenu = menuVisible;
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
        pendingConnectivityTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                pendingConnectivityTimeoutRunnable = null;
                logD("connectivity wait timed out");
                cancelConnectivityWait();
                if (showErrorInMenu) {
                    a.showMenuStatus("Couldn't connect. Tap Next to retry.", true);
                } else {
                    if (a.contentView != null) a.contentView.setText("Couldn't connect. Tap Next to retry.");
                    if (a.contentScroll != null) a.contentScroll.setVisibility(View.VISIBLE);
                    if (a.imageView != null) a.imageView.setVisibility(View.GONE);
                    if (a.logView != null) a.logView.setVisibility(View.VISIBLE);
                    a.forceFullRefresh();
                }
            }
        };
        refreshHandler.postDelayed(pendingConnectivityTimeoutRunnable, CONNECTIVITY_MAX_WAIT_MS);
        setBootStatus("Waiting for WiFi...");
        logD("waiting for connectivity, fetch as soon as up (max " + (CONNECTIVITY_MAX_WAIT_MS / 1000L) + "s)");
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
        forceFullRefresh();
    }

    /** WiFi is only off while sleeping; when app is in foreground, ensure it's on so fetch works.
     * @return true if WiFi was off and we turned it on (caller should delay fetch to allow connection). */
    private boolean ensureWifiOnWhenForeground() {
        if (!ApiPrefs.isAllowSleep(this)) return false;
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifi != null && !wifi.isWifiEnabled()) {
            wifi.setWifiEnabled(true);
            logD("wifi on (app in foreground), wait ~45s for connection");
            return true;
        }
        return false;
    }

    private static final long WIFI_WARMUP_MS = 45 * 1000;

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
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
        if (pendingSleepRunnable != null) {
            refreshHandler.removeCallbacks(pendingSleepRunnable);
            pendingSleepRunnable = null;
        }
        if (pendingWifiWarmupRunnable != null) {
            refreshHandler.removeCallbacks(pendingWifiWarmupRunnable);
            pendingWifiWarmupRunnable = null;
        }
        cancelConnectivityWait();
    }

    @Override
    protected void onDestroy() {
        cancelConnectivityWait();
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
                // Wake 45s early so WiFi warmup finishes by the time we want the next image
                sleepMs = Math.max(0, sleepMs - WIFI_WARMUP_MS);
                scheduleReload(sleepMs);
                setKeepScreenAwake(false);
                WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) wifi.setWifiEnabled(false);
                logD("sleep-ready: alarm in " + (sleepMs / 1000L) + "s (+45s warmup = next image on time; NOOK may blank after idle, e.g. 2m)");
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
        if (contentScroll != null) contentScroll.setVisibility(View.GONE);
        if (logView != null) logView.setVisibility(View.GONE);
        forceFullRefresh();
        logD("displayed generic image");
        logD("next display in " + (refreshMs / 1000L) + "s");
        if (ApiPrefs.isAllowSleep(this)) {
            writeScreenshotToScreensaver(bitmap);
            scheduleReload(refreshMs);
            setKeepScreenAwake(false);
            WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) wifi.setWifiEnabled(false);
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
            new File(dirPath).mkdirs();
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
            logW("screensaver write failed: " + t);
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
        // Always wait for WiFi before attempting fetch
        if (!isConnectedToNetwork(this)) {
            WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && !wifi.isWifiEnabled()) {
                wifi.setWifiEnabled(true);
            }
            waitForWifiThenFetch();
            return;
        }
        if (!ensureCredentials()) {
            return;
        }
        if (fetchInProgress) {
            return;
        }
        fetchInProgress = true;
        fetchStartedFromMenu = menuVisible;
        setBootStatus("Fetching...");
        appendLogLine("Fetching...");
        // Only show Loading in the dialog when user tapped Next. Resume/alarm wake: keep previous display, fetch in background.
        if (menuVisible) {
            showMenuStatus("Loading...", false);
        }
        String httpsUrl = ApiPrefs.getApiBaseUrl(this) + API_DISPLAY_PATH;
        logD("start: " + httpsUrl);
        ApiFetchTask.start(this, httpsUrl, ApiPrefs.getApiId(this), ApiPrefs.getApiToken(this));
    }

    /** Show "Loading..." in content area and hide log so the dialog is clean. */
    private void showLoadingMessage() {
        if (contentView != null) contentView.setText("Loading...");
        if (contentScroll != null) contentScroll.setVisibility(View.VISIBLE);
        if (imageView != null) imageView.setVisibility(View.GONE);
        if (logView != null) logView.setVisibility(View.GONE);
        forceFullRefresh();
    }

    private void scheduleRefresh() {
        if (ApiPrefs.isAllowSleep(this)) {
            return;
        }
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
    }
    
    private void setBootStatus(String status) {
        if (bootStatus != null && !bootComplete) {
            bootStatus.setText("TRMNL  " + status);
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
        }
        if (menuLayout != null) menuLayout.setVisibility(View.VISIBLE);
        if (menuScrim != null) menuScrim.setVisibility(View.VISIBLE);
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
        if (menuLayout != null && menuScrim != null) {
            menuLayout.setVisibility(View.VISIBLE);
            menuScrim.setVisibility(View.VISIBLE);
        }
        menuVisible = true;
        forceFullRefresh();
    }

    /** Restore dialog to Battery / Next / Settings. */
    private void showMenuNormal() {
        if (loadingStatusView != null) loadingStatusView.setVisibility(View.GONE);
        if (batteryView != null) batteryView.setVisibility(View.VISIBLE);
        if (nextButton != null) nextButton.setVisibility(View.VISIBLE);
        if (settingsButton != null) settingsButton.setVisibility(View.VISIBLE);
        forceFullRefresh();
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
        if (!bootComplete) logToScreen(msg);
    }

    private void logW(final String msg) {
        Log.w(TAG, msg);
        if (!bootComplete) logToScreen("W " + msg);
    }
    
    private void logToScreen(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() { appendLogLine(msg); }
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
                
                // Try up to 2 times with 3s backoff
                String bcResult = null;
                for (int attempt = 1; attempt <= 2; attempt++) {
                    if (attempt > 1) {
                        if (a != null) a.logW("Attempt " + (attempt-1) + " failed: " + bcResult + " - retrying in 3s");
                        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                        if (a != null) a.logD("Retrying fetch...");
                    }
                    bcResult = BouncyCastleHttpClient.getHttps(
                            a != null ? a.getApplicationContext() : null,
                            httpsUrl,
                            headers);
                    if (bcResult != null && !bcResult.startsWith("Error:")) {
                        ApiResult parsed = null;
                        if (a != null) {
                            TrmnlApiResponseParser.Result r = TrmnlApiResponseParser.parseAndMaybeFetchImage(
                                    a.getApplicationContext(),
                                    bcResult,
                                    new TrmnlApiResponseParser.Logger() {
                                        public void logD(String msg) { a.logD(msg); }
                                        public void logW(String msg) { a.logW(msg); }
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
            final boolean fromMenu = a.fetchStartedFromMenu;
            a.fetchStartedFromMenu = false;
            if (result instanceof ApiResult) {
                ApiResult ar = (ApiResult) result;
                if (ar.showImage && ar.bitmap != null) {
                    if (ar.rawText != null) {
                        a.logD("response body:\n" + ar.rawText);
                    }
                    a.hideBootScreen();
                    a.imageView.setImageBitmap(ar.bitmap);
                    a.lastDisplayedImage = ar.bitmap;
                    // Always write screensaver immediately so TRMNL appears in NOOK's screensaver list
                    a.writeScreenshotToScreensaver(ar.bitmap);
                    a.imageView.setVisibility(View.VISIBLE);
                    if (a.contentScroll != null) {
                        a.contentScroll.setVisibility(View.GONE);
                    }
                    if (a.logView != null) {
                        a.logView.setVisibility(View.GONE);
                    }
                    a.hideMenu();
                    if (ar.imageUrl != null) {
                        a.logD("image url: " + ar.imageUrl);
                    }
                    a.forceFullRefresh();
                    a.logD("displayed image");
                    a.logD("next display in " + (a.refreshMs / 1000L) + "s");
                    a.scheduleNextCycle();
                    float v = getBatteryVoltage(a);
                    if (v >= 0f) a.logD("Battery-Voltage: " + String.format(Locale.US, "%.1f", v));
                    int rssi = getWifiRssi(a);
                    if (rssi != -999) a.logD("rssi: " + rssi);
                    return;
                }

                // Got API response but no image - show error and schedule retry
                String text = ar.rawText != null ? ar.rawText : "Error: null result";
                a.logD("response body:\n" + text);
                a.logD("no image in response, will retry");
                if (fromMenu) {
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
            // Show error with boot header + logs
            a.hideMenu();
            a.logW("ERROR: " + text);
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
