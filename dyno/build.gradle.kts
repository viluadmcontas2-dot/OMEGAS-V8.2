plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.omegas.dyno.lab"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.omegas.dyno.lab"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
