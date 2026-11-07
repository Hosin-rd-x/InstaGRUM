import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.instaos.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.instaos.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Release signing reads the server keystore properties at
    // /home/ubuntu/instaos/gradle.properties (instaos.storeFile,
    // instaos.storePassword, instaos.keyAlias, instaos.keyPassword).
    // No passwords live in this repo. A release build FAILS if the
    // properties file or any key is absent - there is no fallback
    // password and no silent switch to debug signing.
    val releaseSigningPropsFile = File("/home/ubuntu/instaos/gradle.properties")
    val releaseRequested =
        gradle.startParameter.taskNames.any { it.lowercase().contains("release") }
    val releaseSigningProps = Properties().apply {
        if (releaseSigningPropsFile.exists()) {
            releaseSigningPropsFile.inputStream().use { load(it) }
        } else if (releaseRequested) {
            error("Release signing properties not found: ${releaseSigningPropsFile.path}")
        }
    }

    signingConfigs {
        if (releaseSigningPropsFile.exists()) create("release") {
            storeFile = file(
                releaseSigningProps.getProperty("instaos.storeFile")
                    ?: error("instaos.storeFile missing from ${releaseSigningPropsFile.path}")
            )
            storePassword = releaseSigningProps.getProperty("instaos.storePassword")
                ?: error("instaos.storePassword missing from ${releaseSigningPropsFile.path}")
            keyAlias = releaseSigningProps.getProperty("instaos.keyAlias")
                ?: error("instaos.keyAlias missing from ${releaseSigningPropsFile.path}")
            keyPassword = releaseSigningProps.getProperty("instaos.keyPassword")
                ?: error("instaos.keyPassword missing from ${releaseSigningPropsFile.path}")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    tasks.withType<Test>().configureEach {
        systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        maxHeapSize = "2g"
        // Robolectric/Compose keeps process-global graphics state; isolate test classes.
        forkEvery = 1
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // Encrypted storage for the Instagram web session (SessionStore).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
