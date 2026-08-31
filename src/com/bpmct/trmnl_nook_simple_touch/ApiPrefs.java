package com.bpmct.trmnl_nook_simple_touch;

import android.content.Context;
import android.content.SharedPreferences;

public class ApiPrefs {
    private static final String PREFS_NAME = "trmnl_prefs";
    private static final String KEY_API_ID = "api_id";
    private static final String KEY_API_TOKEN = "api_token";
    private static final String KEY_API_BASE_URL = "api_base_url";
    private static final String DEFAULT_API_BASE_URL = "https://usetrmnl.com/api";
    private static final String KEY_ALLOW_SLEEP = "allow_sleep";
    private static final String KEY_FILE_LOGGING = "file_logging";
    private static final String KEY_GIFT_MODE = "gift_mode";
    private static final String KEY_FRIENDLY_DEVICE_CODE = "friendly_device_code";
    private static final String KEY_GIFT_FROM_NAME = "gift_from_name";
    private static final String KEY_GIFT_TO_NAME = "gift_to_name";
    private static final String KEY_GIFT_WEB_SETUP = "gift_web_setup";
    private static final String KEY_CUSTOM_GIFT_SCREENSAVER = "custom_gift_screensaver_path";
    private static final String KEY_ALLOW_HTTP = "allow_http";
    private static final String KEY_ALLOW_SELF_SIGNED_CERTS = "allow_self_signed_certs";
    private static final String KEY_AUTO_DISABLE_WIFI = "auto_disable_wifi";
    private static final String KEY_SUPER_SLEEP = "super_sleep";
    private static final String KEY_SCREENSAVER_WRITTEN = "screensaver_written_once";
    private static final String KEY_SAVED_SCREEN_TIMEOUT = "saved_screen_timeout_ms";
    private static final String KEY_SHOWCASE_MODE = "showcase_mode";
    private static final String KEY_WIFI_CONNECT_TIMEOUT = "wifi_connect_timeout_seconds";
    /** Default association timeout (seconds). Matches the historical CONNECTIVITY_MAX_WAIT_MS (5s). */
    public static final int DEFAULT_WIFI_CONNECT_TIMEOUT_SECONDS = 5;
    /** Clamp bounds so a bad value can't brick the wake cycle (too short = never connects,
     *  too long = battery drain waiting on a dead network). */
    public static final int MIN_WIFI_CONNECT_TIMEOUT_SECONDS = 5;
    public static final int MAX_WIFI_CONNECT_TIMEOUT_SECONDS = 120;
    private static final String SCREENSAVER_PATH = "/media/screensavers/TRMNL/display.png";

    public static boolean hasCredentials(Context context) {
        return getApiId(context) != null && getApiToken(context) != null;
    }

    public static String getApiId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_API_ID, null);
        if (value == null || value.trim().length() == 0) return null;
        return value.trim();
    }

    public static String getApiToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_API_TOKEN, null);
        if (value == null || value.trim().length() == 0) return null;
        return value.trim();
    }

    public static void saveCredentials(Context context, String apiId, String apiToken) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_API_ID, apiId != null ? apiId.trim() : "")
                .putString(KEY_API_TOKEN, apiToken != null ? apiToken.trim() : "")
                .commit();
    }

    public static String getApiBaseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_API_BASE_URL, null);
        if (value == null || value.trim().length() == 0) {
            return getDefaultApiBaseUrl(context);
        }
        String normalized = normalizeBaseUrl(value, getDefaultApiBaseUrl(context));
        if (!normalized.equals(value.trim())) {
            prefs.edit().putString(KEY_API_BASE_URL, normalized).commit();
        }
        return normalized;
    }

    public static void saveApiBaseUrl(Context context, String baseUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = normalizeBaseUrl(baseUrl, getDefaultApiBaseUrl(context));
        prefs.edit()
                .putString(KEY_API_BASE_URL, value)
                .commit();
    }

    public static String getDefaultApiBaseUrl(Context context) {
        if (context == null) {
            return DEFAULT_API_BASE_URL;
        }
        try {
            return context.getString(R.string.api_base_url_default);
        } catch (Throwable t) {
            return DEFAULT_API_BASE_URL;
        }
    }

    private static String normalizeBaseUrl(String baseUrl, String defaultBaseUrl) {
        String value = baseUrl != null ? baseUrl.trim() : "";
        if (value.length() == 0) {
            return defaultBaseUrl;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        while (value.endsWith("/api/api")) {
            value = value.substring(0, value.length() - 4);
        }
        if (!value.endsWith("/api")) {
            value = value + "/api";
        }
        return value;
    }

    /** Whether the device may sleep between display updates (Electric-Sign-style). Default true. */
    public static boolean isAllowSleep(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ALLOW_SLEEP, false);
    }

    public static void setAllowSleep(Context context, boolean allow) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ALLOW_SLEEP, allow).commit();
    }

    public static boolean isFileLoggingEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_FILE_LOGGING, false);
    }

    public static void setFileLoggingEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_FILE_LOGGING, enabled).commit();
    }

    /** File path for screensaver image (hardcoded for NOOK). */
    public static String getScreensaverPath() {
        return SCREENSAVER_PATH;
    }

    /** The user's original SCREEN_OFF_TIMEOUT captured before the app ever overrides it
     * for forced sleep. 0 = never captured. */
    public static int getSavedScreenTimeout(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SAVED_SCREEN_TIMEOUT, 0);
    }

    public static void setSavedScreenTimeout(Context context, int timeoutMs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_SAVED_SCREEN_TIMEOUT, timeoutMs).commit();
    }

    public static boolean isGiftModeEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_GIFT_MODE, false);
    }

    public static void setGiftModeEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_GIFT_MODE, enabled).commit();
    }

    public static String getFriendlyDeviceCode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_FRIENDLY_DEVICE_CODE, null);
        if (value == null || value.trim().length() == 0) return null;
        return value.trim();
    }

    public static void saveFriendlyDeviceCode(Context context, String code) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_FRIENDLY_DEVICE_CODE, code != null ? code.trim() : "").commit();
    }

    public static String getGiftFromName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_GIFT_FROM_NAME, null);
        if (value == null || value.trim().length() == 0) return null;
        return value.trim();
    }

    public static void saveGiftFromName(Context context, String name) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_GIFT_FROM_NAME, name != null ? name.trim() : "").commit();
    }

    public static String getGiftToName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_GIFT_TO_NAME, null);
        if (value == null || value.trim().length() == 0) return null;
        return value.trim();
    }

    public static void saveGiftToName(Context context, String name) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_GIFT_TO_NAME, name != null ? name.trim() : "").commit();
    }

    /** Whether gift mode should show web-based setup URL instead of manual steps. */
    public static boolean isGiftWebSetup(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_GIFT_WEB_SETUP, false);
    }

    public static void setGiftWebSetup(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_GIFT_WEB_SETUP, enabled).commit();
    }

    /** Custom gift mode screensaver image path on device (e.g. /media/My Files/gift.png). */
    public static String getCustomGiftScreensaverPath(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CUSTOM_GIFT_SCREENSAVER, "");
    }

    public static void setCustomGiftScreensaverPath(Context context, String path) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_CUSTOM_GIFT_SCREENSAVER, path != null ? path.trim() : "").commit();
    }

    /** Whether to allow HTTP (non-HTTPS) connections. Default false. */
    public static boolean isAllowHttp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ALLOW_HTTP, false);
    }

    public static void setAllowHttp(Context context, boolean allow) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ALLOW_HTTP, allow).commit();
    }

    /** Whether to allow self-signed certificates. Default false. */
    public static boolean isAllowSelfSignedCerts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ALLOW_SELF_SIGNED_CERTS, false);
    }

    public static void setAllowSelfSignedCerts(Context context, boolean allow) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ALLOW_SELF_SIGNED_CERTS, allow).commit();
    }

    /** Whether to auto-disable WiFi between fetches for battery saving. Default true. */
    public static boolean isAutoDisableWifi(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_DISABLE_WIFI, true);
    }

    public static void setAutoDisableWifi(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_AUTO_DISABLE_WIFI, enabled).commit();
    }

    // ---------------------------------------------------------------------------
    // Showcase cell credentials (4 independent cells)
    // Each cell has its own token, optional device ID, and optional API base URL.
    // Keys: showcase_cell_token_0..3, showcase_cell_id_0..3, showcase_cell_url_0..3
    // ---------------------------------------------------------------------------

    /** @deprecated Use getShowcaseCellToken(ctx, 0) — kept for SharedPrefs backwards compat */
    public static String getShowcaseDeviceId(Context context, int index) {
        // Old 2-device keys — map device 0→cell 0, device 1→cell 1 for backwards compat
        return getShowcaseCellId(context, index);
    }
    /** @deprecated Use setShowcaseCellToken */
    public static void setShowcaseDeviceId(Context context, int index, String id) {
        setShowcaseCellId(context, index, id);
    }
    /** @deprecated Use getShowcaseCellToken */
    public static String getShowcaseDeviceToken(Context context, int index) {
        return getShowcaseCellToken(context, index);
    }
    /** @deprecated Use setShowcaseCellToken */
    public static void setShowcaseDeviceToken(Context context, int index, String token) {
        setShowcaseCellToken(context, index, token);
    }

    public static String getShowcaseCellId(Context context, int cellIdx) {
        String key = "showcase_cell_id_" + cellIdx;
        // Fall back to old key for cell 0 and 1 (migration)
        if (cellIdx == 0 || cellIdx == 1) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (!prefs.contains(key)) {
                String oldKey = "showcase_device_id_" + cellIdx;
                String oldVal = prefs.getString(oldKey, "");
                if (oldVal != null && oldVal.trim().length() > 0) return oldVal.trim();
            }
        }
        String v = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, "");
        return (v != null) ? v.trim() : "";
    }

    public static void setShowcaseCellId(Context context, int cellIdx, String id) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("showcase_cell_id_" + cellIdx, id != null ? id.trim() : "").commit();
    }

    public static String getShowcaseCellToken(Context context, int cellIdx) {
        String key = "showcase_cell_token_" + cellIdx;
        // Fall back to old key for cell 0 and 1 (migration)
        if (cellIdx == 0 || cellIdx == 1) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (!prefs.contains(key)) {
                String oldKey = "showcase_device_token_" + cellIdx;
                String oldVal = prefs.getString(oldKey, "");
                if (oldVal != null && oldVal.trim().length() > 0) return oldVal.trim();
            }
        }
        String v = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, "");
        return (v != null) ? v.trim() : "";
    }

    public static void setShowcaseCellToken(Context context, int cellIdx, String token) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("showcase_cell_token_" + cellIdx, token != null ? token.trim() : "").commit();
    }

    /** Per-cell API base URL. Empty string means use the global default (usetrmnl.com). */
    public static String getShowcaseCellUrl(Context context, int cellIdx) {
        String v = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("showcase_cell_url_" + cellIdx, "");
        return (v != null) ? v.trim() : "";
    }

    public static void setShowcaseCellUrl(Context context, int cellIdx, String url) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("showcase_cell_url_" + cellIdx, url != null ? url.trim() : "").commit();
    }

    // ---------------------------------------------------------------------------
    // Showcase cell names (4 cells, one name each)
    // ---------------------------------------------------------------------------

    public static String getCellName(Context context, int cellIdx) {
        String key = "showcase_cell_name_" + cellIdx;
        String v = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, "");
        return (v != null) ? v.trim() : "";
    }

    public static void setCellName(Context context, int cellIdx, String name) {
        String key = "showcase_cell_name_" + cellIdx;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(key, name != null ? name.trim() : "").commit();
    }

    public static boolean isShowcaseModeEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SHOWCASE_MODE, false);
    }

    public static void setShowcaseModeEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_SHOWCASE_MODE, enabled).commit();
    }


    /** Whether to sleep immediately after a new image loads (timer/alarm fetches only). Default false. */
    public static boolean isSuperSleep(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SUPER_SLEEP, false);
    }

    public static void setSuperSleep(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_SUPER_SLEEP, enabled).commit();
    }

    /**
     * WiFi association timeout in seconds. This is how long the app waits for the
     * network to come up after a wake before giving up and showing the no-WiFi
     * screen. Some access points / busy 2.4GHz environments take longer than the
     * 5s default to associate (see issue #44), so this is user-configurable.
     * Returned value is clamped to [MIN, MAX]. Default is
     * DEFAULT_WIFI_CONNECT_TIMEOUT_SECONDS (preserves prior behavior).
     */
    public static int getWifiConnectTimeoutSeconds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int seconds = prefs.getInt(KEY_WIFI_CONNECT_TIMEOUT, DEFAULT_WIFI_CONNECT_TIMEOUT_SECONDS);
        if (seconds < MIN_WIFI_CONNECT_TIMEOUT_SECONDS) seconds = MIN_WIFI_CONNECT_TIMEOUT_SECONDS;
        if (seconds > MAX_WIFI_CONNECT_TIMEOUT_SECONDS) seconds = MAX_WIFI_CONNECT_TIMEOUT_SECONDS;
        return seconds;
    }

    public static void setWifiConnectTimeoutSeconds(Context context, int seconds) {
        if (seconds < MIN_WIFI_CONNECT_TIMEOUT_SECONDS) seconds = MIN_WIFI_CONNECT_TIMEOUT_SECONDS;
        if (seconds > MAX_WIFI_CONNECT_TIMEOUT_SECONDS) seconds = MAX_WIFI_CONNECT_TIMEOUT_SECONDS;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_WIFI_CONNECT_TIMEOUT, seconds).commit();
    }

    /** Whether the initial screensaver has been written to disk at least once. */
    public static boolean isScreensaverWrittenOnce(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SCREENSAVER_WRITTEN, false);
    }

    public static void setScreensaverWrittenOnce(Context context, boolean written) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_SCREENSAVER_WRITTEN, written).commit();
    }
}
