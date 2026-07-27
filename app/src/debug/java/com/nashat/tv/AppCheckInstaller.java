package com.nashat.tv;

import android.content.Context;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

/**
 * Debug variant: installs the App Check debug provider.
 *
 * <p>This lives in the {@code debug} source set because
 * {@code firebase-appcheck-debug} is a {@code debugImplementation} dependency.
 * It used to be imported directly from {@code NashatTvApp}, which meant the
 * release variant referenced a class that is not on its classpath and
 * {@code assembleRelease} failed to compile at all.
 */
final class AppCheckInstaller {

    static void install(Context context) {
        FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
    }

    private AppCheckInstaller() {}
}
