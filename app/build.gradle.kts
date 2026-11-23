plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val githubUpdates = providers.gradleProperty("nobi.githubUpdates").map(String::toBooleanStrict).orElse(true)

android {
    namespace = "com.nobi.scan"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nobi.scan"
        minSdk = 32
        targetSdk = 36
        versionCode = 10005
        versionName = "1.0.0-rc6"

        buildConfigField("boolean", "GITHUB_UPDATES", githubUpdates.get().toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    if (githubUpdates.get()) {
        sourceSets.getByName("release").manifest.srcFile("src/github/AndroidManifest.xml")
    }

    buildTypes {
        debug { buildConfigField("boolean", "GITHUB_UPDATES", "false") }
        release {
            ndk.abiFilters.add("arm64-v8a")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs.useLegacyPackaging = false
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Accompanist Permissions
    implementation(libs.accompanist.permissions)

    // OpenCV
    implementation(libs.opencv)
    implementation(libs.glasmio.glass)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    testImplementation("junit:junit:4.13.2")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
