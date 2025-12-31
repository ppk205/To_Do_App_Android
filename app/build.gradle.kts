plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Read local.properties
import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use { localProperties.load(it) }
}

android {
    namespace = "com.example.morp_prj"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.morp_prj"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Add Cloudinary config to BuildConfig
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${localProperties.getProperty("cloudinary.cloud_name", "dxohngowm")}\"")
        buildConfigField("String", "CLOUDINARY_API_KEY", "\"${localProperties.getProperty("cloudinary.api_key", "687411225619873")}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${localProperties.getProperty("cloudinary.upload_preset", "upload_project")}\"")
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true // ✅ Enable BuildConfig generation
        viewBinding = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
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
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:3.12.0")

    // Image loading
    implementation("com.github.bumptech.glide:glide:4.14.2")
    annotationProcessor("com.github.bumptech.glide:compiler:4.14.2")

    // Room (SQLite)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Security - Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Biometric Authentication
    implementation("androidx.biometric:biometric:1.1.0")

    // Donut Chart
    implementation("com.github.PhilJay:MPAndroidChart:3.1.0")

    // WorkManager (background notifications)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}