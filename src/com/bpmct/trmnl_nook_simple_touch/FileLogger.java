package com.bpmct.trmnl_nook_simple_touch;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Writes log entries to /media/My Files/trmnl.log
 */
public class FileLogger {
    private static final String LOG_PATH = "/media/My Files/trmnl.log";
    private static final long MAX_SIZE = 512 * 1024; // 512KB max
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public static synchronized void log(String tag, String level, String msg) {
        try {
            File f = new File(LOG_PATH);
            // Rotate if too large
            if (f.exists() && f.length() > MAX_SIZE) {
                File old = new File(LOG_PATH + ".old");
                old.delete();
                f.renameTo(old);
            }
            PrintWriter pw = new PrintWriter(new FileWriter(LOG_PATH, true));
            pw.println(SDF.format(new Date()) + " " + level + "/" + tag + ": " + msg);
            pw.close();
        } catch (Throwable t) {
            // Ignore file write failures
        }
    }

    public static void d(String tag, String msg) {
        log(tag, "D", msg);
    }

    public static void w(String tag, String msg) {
        log(tag, "W", msg);
    }

    public static void e(String tag, String msg) {
        log(tag, "E", msg);
    }

    public static void e(String tag, String msg, Throwable t) {
        String full = msg;
        if (t != null) {
            full += ": " + t.toString();
        }
        log(tag, "E", full);
    }
}
