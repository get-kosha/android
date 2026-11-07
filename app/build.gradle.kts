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
    }
    buildTypes {
        release {
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
    implementation(libs.core.splashscreen)
    implementation(libs.fragment)
    implementation(libs.lifecycle.process)
}
