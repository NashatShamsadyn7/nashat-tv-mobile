package com.nashat.tv;

import android.content.Context;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/** Release variant: attests the app with Play Integrity. */
final class AppCheckInstaller {

    static void install(Context context) {
        FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
    }

    private AppCheckInstaller() {}
}
