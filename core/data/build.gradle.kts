plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.getkosha.data"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
}
