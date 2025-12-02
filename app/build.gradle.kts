import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.mytrackr.receipts"
    compileSdk = 36

    val properties = Properties().apply {
        load(project.rootProject.file("local.properties").inputStream())
    }
    val geminiKey = properties["GEMINI_API_KEY"] as String

    defaultConfig {
        applicationId = "com.mytrackr.receipts"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.airbnb.android:lottie:+")
    // Firebase Dependencies
    implementation(platform("com.google.firebase:firebase-bom:34.3.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.firebase:firebase-firestore")
    // Firebase Storage for saving receipt images
    implementation("com.google.firebase:firebase-storage")
    // Firebase Cloud Messaging for push notifications
    implementation("com.google.firebase:firebase-messaging")
    // ML Kit on-device Text Recognition (use latest stable suggested)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // ML Kit Document Scanner for cropping and enhancing receipt images
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")    // Lifecycle (ViewModel + LiveData)

    // ML Kit for Receipt Scanning
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Lifecycle (ViewModel + LiveData)
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.6")

    // ViewPager2 and Onboarding
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("com.tbuonomo:dotsindicator:5.0")

    // Glide for efficient image loading, EXIF handling and transformations
    implementation("com.github.bumptech.glide:glide:4.15.1")

    // Cloudinary (unsigned uploads from client with an upload preset) and OkHttp for multipart upload
    implementation("com.cloudinary:cloudinary-android:3.1.2")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")


    // WorkManager for background tasks and scheduled notifications
    implementation("androidx.work:work-runtime:2.9.0")

    // Guava for ListenableFuture (required by WorkManager)
    implementation("com.google.guava:guava:31.1-android")

    // Chart for dashboard
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Gemini Developer API client (Generative AI)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
}