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
        return prefs.getBoolean(KEY_ALLOW_SLEEP, true);
    }

    public static void setAllowSleep(Context context, boolean allow) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ALLOW_SLEEP, allow).commit();
    }

    /** File path for screensaver image (hardcoded for NOOK). */
    public static String getScreensaverPath() {
        return SCREENSAVER_PATH;
    }
}
