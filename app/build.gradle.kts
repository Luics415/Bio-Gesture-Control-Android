plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.luics415.biogesture"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.luics415.biogesture"
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val storePath = providers.gradleProperty("BIOGESTURE_STORE_FILE").orNull
        val storePasswordValue = providers.gradleProperty("BIOGESTURE_STORE_PASSWORD").orNull
        val keyAliasValue = providers.gradleProperty("BIOGESTURE_KEY_ALIAS").orNull
        val keyPasswordValue = providers.gradleProperty("BIOGESTURE_KEY_PASSWORD").orNull
        if (listOf(storePath, storePasswordValue, keyAliasValue, keyPasswordValue).all { it != null }) {
            create("release") {
                storeFile = file(checkNotNull(storePath))
                storePassword = checkNotNull(storePasswordValue)
                keyAlias = checkNotNull(keyAliasValue)
                keyPassword = checkNotNull(keyPasswordValue)
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
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // MediaPipe para detección de manos
    implementation(libs.mediapipe.tasks.vision)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

}
