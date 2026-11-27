import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

val rawTagVersion = (System.getenv("GITHUB_REF_NAME") ?: "")
    .removePrefix("v")
    .trim()
    .takeIf { it.matches(Regex("^\\d+(\\.\\d+)*$")) }
    ?: "1.1.1"

val computedVersionCode = rawTagVersion
    .split(".")
    .mapNotNull { it.toIntOrNull() }
    .let { parts ->
        val major = parts.getOrElse(0) { 1 }.coerceAtMost(2000)
        val minor = parts.getOrElse(1) { 0 }.coerceIn(0, 999)
        val patch = parts.getOrElse(2) { 0 }.coerceIn(0, 999)
        major * 1_000_000 + minor * 1_000 + patch
    }

android {
    namespace = "com.moonreader.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.moonreader.app"
        minSdk = 26
        targetSdk = 34
        versionCode = computedVersionCode
        versionName = rawTagVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("MOONREADER_KEYSTORE_PATH")
                ?: keystoreProps.getProperty("storeFile")
            if (!storePath.isNullOrBlank()) {
                val resolvedFile = rootProject.file(storePath)
                if (resolvedFile.exists()) {
                    storeFile = resolvedFile
                    storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                        ?: keystoreProps.getProperty("storePassword")
                    keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                        ?: keystoreProps.getProperty("keyAlias")
                    keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                        ?: keystoreProps.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storeFile != null) {
                signingConfig = releaseConfig
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
