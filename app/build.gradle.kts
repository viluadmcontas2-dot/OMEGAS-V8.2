import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseManifestFile = rootProject.file("config/omegas-release.json")
require(releaseManifestFile.isFile) { "Manifesto de release ausente: ${releaseManifestFile.path}" }

@Suppress("UNCHECKED_CAST")
val releaseManifest = JsonSlurper().parseText(releaseManifestFile.readText()) as Map<String, Any?>

fun releaseString(key: String): String = releaseManifest[key]?.toString()?.takeIf { it.isNotBlank() }
    ?: error("Campo obrigatório ausente em config/omegas-release.json: $key")

fun releaseInt(key: String): Int = when (val value = releaseManifest[key]) {
    is Number -> value.toInt()
    else -> value?.toString()?.toIntOrNull()
        ?: error("Campo inteiro inválido em config/omegas-release.json: $key")
}

fun releaseBoolean(key: String): Boolean = when (val value = releaseManifest[key]) {
    is Boolean -> value
    else -> value?.toString()?.toBooleanStrictOrNull()
        ?: error("Campo booleano inválido em config/omegas-release.json: $key")
}

fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val buildCommit = providers.environmentVariable("OMEGAS_SOURCE_SHA")
    .orElse(providers.environmentVariable("GITHUB_SHA"))
    .orElse("local")
    .get()
    .take(12)

val targetAbis = providers.gradleProperty("omegasAbis")
    .orElse("arm64-v8a")
    .get()
    .split(',')
    .map(String::trim)
    .filter(String::isNotBlank)

val releaseSigningProperties = rootProject.file("keystore.properties")
    .takeIf { it.isFile }
    ?.inputStream()
    ?.use { input -> Properties().apply { load(input) } }

android {
    namespace = "com.omegas.prohub"
    compileSdk = 35

    defaultConfig {
        applicationId = releaseString("applicationId")
        minSdk = 26
        targetSdk = 35
        versionCode = releaseInt("versionCode")
        versionName = releaseString("versionName")
        resValue("string", "app_name", releaseString("appLabel"))

        buildConfigField("String", "OMEGAS_PRODUCT", buildConfigString(releaseString("product")))
        buildConfigField("String", "OMEGAS_APP_LABEL", buildConfigString(releaseString("appLabel")))
        buildConfigField("String", "OMEGAS_GENERATION", buildConfigString(releaseString("generation")))
        buildConfigField("String", "OMEGAS_CHANNEL", buildConfigString(releaseString("channel")))
        buildConfigField("String", "OMEGAS_ENGINE", buildConfigString(releaseString("engine")))
        buildConfigField("String", "OMEGAS_TELEMETRY_SCHEMA", buildConfigString(releaseString("telemetrySchema")))
        buildConfigField("String", "OMEGAS_LEARNING_SCHEMA", buildConfigString(releaseString("learningSchema")))
        buildConfigField("String", "OMEGAS_MAP_SCHEMA", buildConfigString(releaseString("mapSchema")))
        buildConfigField("String", "OMEGAS_K_FACTOR_SCHEMA", buildConfigString(releaseString("kFactorSchema")))
        buildConfigField("String", "OMEGAS_K_FACTOR_STATE", buildConfigString(releaseString("kFactorState")))
        buildConfigField("String", "OMEGAS_SAFETY_MODE", buildConfigString(releaseString("safetyMode")))
        buildConfigField("String", "OMEGAS_BUILD_COMMIT", buildConfigString(buildCommit))
        buildConfigField("boolean", "OMEGAS_AUTOMATIC_CALIBRATION", releaseBoolean("automaticCalibration").toString())

        ndk {
            abiFilters += targetAbis
        }
    }

    signingConfigs {
        if (releaseSigningProperties != null) {
            create("omegasRelease") {
                storeFile = file(requireNotNull(releaseSigningProperties.getProperty("storeFile")))
                storePassword = requireNotNull(releaseSigningProperties.getProperty("storePassword"))
                storeType = releaseSigningProperties.getProperty("storeType", "JKS")
                keyAlias = requireNotNull(releaseSigningProperties.getProperty("keyAlias"))
                keyPassword = requireNotNull(releaseSigningProperties.getProperty("keyPassword"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("omegasRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

