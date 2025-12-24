plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Optional: only if you're using Compose
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.chattingapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chattingapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"http://192.168.100.12:8080/chattingapp/\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://nxagvhkldfxenczfahch.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im54YWd2aGtsZGZ4ZW5jemZhaGNoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDc0ODc4NTUsImV4cCI6MjA2MzA2Mzg1NX0.6fxDR2kDAzIbey_anvHyOM6FKhbcvOfPN0LMsKp2BuE\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        //noinspection DataBindingWithoutKapt
        dataBinding = true
        // Enable Compose only if you’re using it
        compose = true
        buildConfig = true
        //noinspection DataBindingWithoutKapt
        dataBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13" // Compatible with Kotlin 2.0.21 (auto-managed)
    }
}

dependencies {

    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    // (Removed) Material Components UI
    implementation(libs.androidx.constraintlayout)

    // Lifecycle components
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Compose (Optional - only if you use Compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Animations, transitions, etc.
    implementation("androidx.transition:transition:1.4.1")
    implementation("com.airbnb.android:lottie:6.1.0")
    implementation("androidx.cardview:cardview:1.0.0")


    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Volley Backend
    implementation("com.android.volley:volley:1.2.1")

    // (Removed) Google Sign-In

    // Socket.IO client for Android
    implementation("io.socket:socket.io-client:2.0.1")
    implementation("org.json:json:20231013")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Okhttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Supabase Realtime
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.2"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")

    // Ktor (required by Supabase SDK)
    implementation("io.ktor:ktor-client-android:3.0.1")
    implementation("io.ktor:ktor-client-core:3.0.1")
    implementation("io.ktor:ktor-client-cio:3.0.1")
    implementation("io.ktor:ktor-client-websockets:3.0.1")
    implementation("io.ktor:ktor-utils:3.0.1")

    // Kotlin Serialization (already added)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Waveform
    implementation("com.github.lincollincol:amplituda:2.2.2")

    // Format Time
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    // Realtime setup
    implementation ("io.github.jan-tennert.supabase:realtime-kt:2.0.0")
    implementation ("io.github.jan-tennert.supabase:postgrest-kt:2.0.0")
    implementation ("io.ktor:ktor-client-okhttp:2.3.7")

    implementation("com.github.bumptech.glide:glide:4.15.1")

    implementation("androidx.camera:camera-core:1.2.3")
    implementation("androidx.camera:camera-camera2:1.2.3")
    implementation("androidx.camera:camera-lifecycle:1.2.3")
    implementation("androidx.camera:camera-video:1.2.3")
    implementation("androidx.camera:camera-view:1.2.3")
    implementation("androidx.camera:camera-extensions:1.2.3")

    // For permissions
    implementation("com.guolindev.permissionx:permissionx:1.7.1")
}