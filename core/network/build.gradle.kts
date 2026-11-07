plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.getkosha.network"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
}
