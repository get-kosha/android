import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.getkosha"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.getkosha"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The tour test signs in to a Kosha dev server reached through `adb reverse tcp:3000 tcp:3000`.
        testInstrumentationRunnerArguments["kosha.server"] = "http://localhost:3000"
    }
    // Play upload key, from keystore.properties (git-ignored): storeFile, storePassword, keyAlias, keyPassword.
    // Without it the release build is unsigned.
    val keystore = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }
    signingConfigs {
        if (keystore != null) {
            create("upload") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:add"))
    implementation(project(":feature:overview"))
    implementation(project(":feature:activity"))
    implementation(project(":feature:recurring"))
    implementation(project(":feature:accounts"))
    implementation(project(":feature:statements"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:welcome"))
    implementation(libs.activity.compose)
    implementation(libs.browser)
    implementation(libs.core.splashscreen)
    implementation(libs.fragment)
    implementation(libs.lifecycle.process)
    implementation(libs.glance.appwidget)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.runner)
    // Compose's test library brings an older Espresso that calls an input API Android 17 removed.
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
