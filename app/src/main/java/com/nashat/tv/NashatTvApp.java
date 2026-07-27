package com.nashat.tv;

import android.app.Application;
import android.os.Debug;
import android.util.Log;

import com.google.firebase.FirebaseApp;

import java.io.File;

public class NashatTvApp extends Application {

    private static final String TAG = "NashatTvApp";

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);

        // The provider differs per build variant, so it is resolved through a
        // per-source-set class rather than a BuildConfig.DEBUG branch — the
        // debug provider is not on the release classpath at all.
        try {
            AppCheckInstaller.install(this);
        } catch (Throwable t) {
            // App Check is an integrity signal, not a prerequisite for the app
            // to run. Never let it take the process down at startup.
            Log.w(TAG, "App Check unavailable", t);
        }

        if (!BuildConfig.DEBUG) {
            // Lightweight tamper signals — Play Integrity (via App Check) is
            // the authoritative check, this just trips early on obviously
            // compromised devices and is logged for diagnostics.
            if (isProbablyRooted() || isDebuggerAttached()) {
                Log.w(TAG, "Untrusted runtime detected");
            }
        }
    }

    private static boolean isDebuggerAttached() {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger();
    }

    private static boolean isProbablyRooted() {
        String[] suspectPaths = {
                "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/system/app/Superuser.apk", "/system/etc/init.d/99SuperSUDaemon",
                "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su", "/system/bin/.ext/.su",
                "/system/usr/we-need-root/", "/cache/su", "/data/su"
        };
        for (String p : suspectPaths) {
            try {
                if (new File(p).exists()) return true;
            } catch (SecurityException ignored) {
                return true;
            }
        }
        String tags = android.os.Build.TAGS;
        return tags != null && tags.contains("test-keys");
    }
}
