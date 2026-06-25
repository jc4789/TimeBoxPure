import java.net.URI

plugins {
  alias(libs.plugins.android.application)
  kotlin("android")
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  kotlin("plugin.serialization") version "2.2.10"
}

android {
  namespace = "com.example.timeboxvibe"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.timeboxvibe.pxqyva"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
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
    compose = false
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}



dependencies {
    implementation(project(":shared-engine"))

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.datastore.preferences)
	implementation("androidx.activity:activity-ktx:1.9.3")

	implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
	implementation("androidx.lifecycle:lifecycle-process:2.8.7")
	implementation(libs.androidx.lifecycle.runtime.ktx)

	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.kotlinx.coroutines.core)
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

