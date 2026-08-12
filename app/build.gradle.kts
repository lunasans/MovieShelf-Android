@file:Suppress("DEPRECATION")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt")
}

android {
    namespace = "info.movieshelf"
    compileSdk = 36

    defaultConfig {
        applicationId = "info.movieshelf"
        minSdk = 24
        targetSdk = 36
        versionCode = 30
        versionName = "2.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CI-Signierung: Der Play-Release-Workflow stellt den Upload-Keystore über
    // Umgebungsvariablen bereit. Lokale Builds (Android-Studio-Wizard) bleiben
    // unverändert, weil die Config nur bei gesetztem ANDROID_KEYSTORE_FILE greift.
    val ciKeystorePath: String? = System.getenv("ANDROID_KEYSTORE_FILE")
    if (ciKeystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
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
            ndk {
                // Nativer Code kommt nur aus (bereits gestrippten) Drittanbieter-Libs
                // (ML Kit, CameraX). SYMBOL_TABLE extrahiert die noch vorhandene
                // Symboltabelle; FULL fände keine Debug-Infos. Erfordert installiertes NDK.
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            if (ciKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    ndkVersion = "30.0.14904198 rc1"
    buildToolsVersion = "36.0.0"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            // Annotationen an Konstruktor-Parametern (etwa @StringRes in UiText)
            // gelten kuenftig auch fuer das erzeugte Feld. Die Umstellung jetzt
            // mitzumachen ist die Richtung, in die Kotlin ohnehin geht — sonst
            // aendert sich das Verhalten spaeter unangekuendigt (KT-73255).
            "-Xannotation-default-target=param-property"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.browser)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.app.update.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)

    // Guava
    implementation(libs.guava)

    testImplementation(libs.junit)
    // Room gegen eine echte SQLite-Datenbank pruefen. Handgeschriebene
    // Doubles koennen die Eigenheiten von SQLite nicht nachbilden - genau
    // daran ist der Verlust der Besetzung durch INSERT OR REPLACE
    // vorbeigelaufen.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
