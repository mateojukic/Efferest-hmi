plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.efferest_hmi"

    // Keep your compileSdk config. If this "release(36)" is from the catalog, leave it.
    // If syncing fails, change to: compileSdk = 34
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.efferest_hmi"

        // Automotive apps typically require minSdk 29+. Update now to avoid issues on AAOS.
        minSdk = 29
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Enable Jetpack Compose
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    // Recommended toolchain levels for modern AGP/Compose
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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

    useLibrary("android.car")
}

dependencies {
    // Existing dependencies are fine to keep
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose BOM to align versions
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose + Material3
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material:material-icons-extended-android:1.6.0")
    implementation("androidx.compose.material:material-icons-extended-android:1.6.0-beta01")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Lifecycle & ViewModel helpers
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Tooling & Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Material icon set for placeholder icons (works with your Compose BOM)
    implementation("androidx.compose.material:material-icons-extended")

    // Android car dependency
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-automotive:1.7.0")
}