package com.bpmct.trmnl_nook_simple_touch;

import java.net.URLDecoder;
import java.util.Hashtable;

import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

/**
 * Helper for parsing TRMNL API display responses and downloading/decoding the image.
 *
 * This is extracted from DisplayActivity for readability; behavior should match.
 */
final class TrmnlApiResponseParser {

    interface Logger {
        void logD(String msg);
        void logW(String msg);
    }

    static final class Result {
        final String rawText;
        final boolean showImage;
        final Bitmap bitmap;
        final String imageUrl;
        final int refreshRateSeconds;

        Result(String rawText) {
            this.rawText = rawText;
            this.showImage = false;
            this.bitmap = null;
            this.imageUrl = null;
            this.refreshRateSeconds = -1;
        }

        Result(String rawText, int refreshRateSeconds, String imageUrl, Bitmap bitmap) {
            this.rawText = rawText;
            this.showImage = true;
            this.bitmap = bitmap;
            this.imageUrl = imageUrl;
            this.refreshRateSeconds = refreshRateSeconds;
        }
    }

    private TrmnlApiResponseParser() {}

    static Result parseAndMaybeFetchImage(Context ctx, String jsonText, Logger log) {
        try {
            JSONObject obj = new JSONObject(jsonText);
            int status = obj.optInt("status", -1);
            // API returns 0 for display
            if (status != 0 && status != 200) {
                return new Result(jsonText);
            }
            if (log != null) log.logD("api status: " + status);

            int refreshRateSeconds = obj.optInt("refresh_rate", -1);

            String imageUrl = obj.optString("image_url", null);
            if (imageUrl == null || imageUrl.length() == 0) {
                return new Result(jsonText);
            }
            if (log != null) log.logD("api image_url: " + imageUrl);

            // Log a decoded URL for readability, but use the encoded URL for fetch.
            try {
                String decoded = URLDecoder.decode(imageUrl, "UTF-8");
                if (log != null) log.logD("decoded image url: " + decoded);
            } catch (Throwable ignored) {
            }

            Hashtable headers = new Hashtable();
            headers.put("User-Agent", "TRMNL-Nook/1.0 (Android 2.1)");
            headers.put("Accept", "image/*");

            byte[] imageBytes = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                if (attempt > 1) {
                    if (log != null) log.logW("Image fetch attempt " + (attempt - 1) + " failed - retrying in 3s");
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
                imageBytes = BouncyCastleHttpClient.getHttpsBytes(ctx, imageUrl, headers);
                if (imageBytes != null && imageBytes.length > 0) break;
            }

            if (imageBytes == null || imageBytes.length == 0) {
                if (log != null) log.logW("image fetch failed after retries for url: " + imageUrl);
                return new Result("Error: Failed to download image from " + imageUrl);
            }
            if (log != null) log.logD("image bytes: " + imageBytes.length);

            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap == null) {
                if (log != null) log.logW("image decode failed");
                return new Result(jsonText);
            }
            if (imageUrl.endsWith("/empty_state.bmp")) {
                bitmap = rotate90(bitmap);
            }

            return new Result(jsonText, refreshRateSeconds, imageUrl, bitmap);
        } catch (Throwable t) {
            if (log != null) log.logW("response parse failed: " + t);
            return new Result(jsonText);
        }
    }

    private static Bitmap rotate90(Bitmap src) {
        try {
            Matrix m = new Matrix();
            m.postRotate(90f);
            return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        } catch (Throwable t) {
            return src;
        }
    }
}
