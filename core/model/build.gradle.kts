// Pure Kotlin: money, keypad maths and other logic with no Android in it, so it's unit tested on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

dependencies {
    testImplementation(libs.junit)
}
