// Top-level build file. Plugin versions are declared once in
// gradle/libs.versions.toml and applied in :app, so no module pins its own.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.crashlytics) apply false
}
