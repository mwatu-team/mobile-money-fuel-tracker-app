import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Debug builds read development defaults (backend URL, pilot device credentials) from
// local.properties, which is gitignored, so real tokens never land in git history.
// Missing keys fall back to the emulator-friendly defaults.
fun loadProperties(file: java.io.File) = Properties().apply {
    if (file.exists()) FileInputStream(file).use { load(it) }
}

val localProperties = loadProperties(rootProject.file("local.properties"))

// Release builds use the committed, non-secret production config. The environment variable
// overrides it (for CI); signing secrets come only from the environment or local.properties.
val productionProperties = loadProperties(rootProject.file("config/production.properties"))

fun secret(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseBackendUrl: String = (System.getenv("MMFT_RELEASE_BACKEND_URL")?.takeIf { it.isNotBlank() }
    ?: productionProperties.getProperty("gatewayBackendBaseUrl")
    ?: "").trim()
val privacyPolicyUrl: String = productionProperties.getProperty("privacyPolicyUrl", "").trim()

// Fail the build rather than ship an app that would send payment data over cleartext HTTP.
if (!releaseBackendUrl.startsWith("https://") || !releaseBackendUrl.endsWith("/")) {
    throw GradleException(
        "Release backend URL must be an https:// URL ending in \"/\" " +
            "(config/production.properties or MMFT_RELEASE_BACKEND_URL), got: \"$releaseBackendUrl\"",
    )
}

val uploadKeystorePath = secret("MMFT_UPLOAD_KEYSTORE_FILE", "uploadKeystoreFile")
val uploadKeystorePassword = secret("MMFT_UPLOAD_KEYSTORE_PASSWORD", "uploadKeystorePassword")
val uploadKeyAlias = secret("MMFT_UPLOAD_KEY_ALIAS", "uploadKeyAlias")
val uploadKeyPassword = secret("MMFT_UPLOAD_KEY_PASSWORD", "uploadKeyPassword")
val canSignRelease = listOf(uploadKeystorePath, uploadKeystorePassword, uploadKeyAlias, uploadKeyPassword)
    .all { it != null }

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.mobilemoneyfueltracker.gateway"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mobilemoneyfueltracker.gateway"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(uploadKeystorePath!!)
                storePassword = uploadKeystorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "DEFAULT_BACKEND_BASE_URL",
                quoted(localProperties.getProperty("gatewayBackendBaseUrl", "http://10.0.2.2:8000/")),
            )
            buildConfigField(
                "String",
                "DEFAULT_GATEWAY_DEVICE_TOKEN",
                quoted(localProperties.getProperty("gatewayDeviceToken", "")),
            )
            buildConfigField(
                "String",
                "DEFAULT_GATEWAY_DEVICE_IDENTIFIER",
                quoted(localProperties.getProperty("gatewayDeviceIdentifier", "")),
            )
            buildConfigField("String", "PRIVACY_POLICY_URL", quoted(privacyPolicyUrl))
        }
        release {
            // A release must never carry a credential: phones are paired at runtime.
            buildConfigField("String", "DEFAULT_BACKEND_BASE_URL", quoted(releaseBackendUrl))
            buildConfigField("String", "DEFAULT_GATEWAY_DEVICE_TOKEN", "\"\"")
            buildConfigField("String", "DEFAULT_GATEWAY_DEVICE_IDENTIFIER", "\"\"")
            buildConfigField("String", "PRIVACY_POLICY_URL", quoted(privacyPolicyUrl))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("Release signing is not configured (MMFT_UPLOAD_* ); the release bundle will be unsigned.")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    testOptions.unitTests.isIncludeAndroidResources = true
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
