// Pure Kotlin: the API's shapes, money, dates, keypad maths and quick add. No Android, so it's unit tested on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.serialization.json)
    testImplementation(libs.junit)
}
