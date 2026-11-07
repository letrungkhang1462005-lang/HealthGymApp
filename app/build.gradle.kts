import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {

    namespace = "com.example.healthgymapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.healthgymapp"
        minSdk = 25
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Đọc GEMINI_API_KEY từ local.properties và đưa vào BuildConfig
        val localProps = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localFile.inputStream().use { localProps.load(it) }
        }
        val geminiKey = localProps.getProperty("GEMINI_API_KEY", "")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // AndroidX & UI Components
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Architecture Components (ViewModel & LiveData)
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    // Retrofit 2 & Gson (Kết nối Gemini AI API & Parse JSON)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // MPAndroidChart (Vẽ biểu đồ tiến trình cân nặng & Macro)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    // Thư viện Guava để xử lý ListenableFuture / Futures cho Gemini Java SDK
    implementation("com.google.guava:guava:31.1-android")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}