import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.photomed.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.photomed.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.11"
    }

    // ML Kit incluye bibliotecas nativas por arquitectura: un APK por ABI para instalación directa
    // (en Google Play, el App Bundle ya entrega solo la necesaria).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        // Firma de publicación desde keystore.properties (no versionado). Sin ese archivo,
        // la build release se firma con la clave de depuración solo para pruebas locales.
        val props = rootProject.file("keystore.properties")
        if (props.exists()) {
            val p = Properties().apply { props.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    androidResources {
        // El modelo de MediaPipe se mapea en memoria: no debe comprimirse.
        noCompress += listOf("task")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    testImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    val cameraX = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // Puntos anatómicos en el dispositivo (modelos incluidos en el APK, sin red)
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    // Motor alternativo de puntos faciales (478 puntos + pose 3D), Apache 2.0
    implementation("com.google.mediapipe:tasks-vision:1.2.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Los documentos legales viven en la raíz del repo (LICENSE, PRIVACY.md, TERMS.md)
// y se empaquetan como assets para mostrarlos dentro de la aplicación.
val copyLegalDocs by tasks.registering(Copy::class) {
    from(rootProject.file("PRIVACY.md"), rootProject.file("TERMS.md"), rootProject.file("CHANGELOG.md"))
    from(rootProject.file("LICENSE")) { rename { "LICENSE.md" } }
    into(layout.buildDirectory.dir("generated/legal/legal"))
}
android.sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/legal"))
tasks.named("preBuild") { dependsOn(copyLegalDocs) }
