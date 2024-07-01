import org.jetbrains.kotlin.ir.util.toIrConst

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.crashlytics)
}

android {
    namespace = "xyz.quaver.pupil"
    defaultConfig {
        applicationId = "xyz.quaver.pupil"
        minSdk = libs.versions.android.minSdk.get().toInt()
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 69
        versionName = "6.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            ext.set("enableCrashlytics", false)
            ext.set("alwaysUpdateBuildId", false)
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.datetime)

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.accompanist.adaptive)

    implementation(libs.coil)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.ktor.client)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.documentFileX)
}

//dependencies {
//    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
//    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0"
//    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3"
//    implementation "org.jetbrains.kotlinx:kotlinx-datetime:0.5.0"
//
//    implementation "androidx.appcompat:appcompat:1.7.0"
//    implementation "androidx.activity:activity-ktx:1.9.0"
//    implementation "androidx.fragment:fragment-ktx:1.8.1"
//    implementation "androidx.preference:preference-ktx:1.2.1"
//    implementation "androidx.recyclerview:recyclerview:1.3.2"
//    implementation "androidx.constraintlayout:constraintlayout:2.1.4"
//    implementation "androidx.gridlayout:gridlayout:1.0.0"
//    implementation "androidx.biometric:biometric:1.1.0"
//    implementation "androidx.work:work-runtime-ktx:2.9.0"
//
//    implementation platform("androidx.compose:compose-bom:2024.06.00")
//
//    implementation "androidx.compose.material3:material3"
//    implementation "androidx.compose.material3:material3-window-size-class"
//    implementation 'androidx.compose.foundation:foundation'
//    implementation 'androidx.compose.ui:ui'
//    implementation 'androidx.compose.ui:ui-tooling-preview'
//    debugImplementation 'androidx.compose.ui:ui-tooling'
//    androidTestImplementation 'androidx.compose.ui:ui-test-junit4:1.6.8'
//    debugImplementation 'androidx.compose.ui:ui-test-manifest'
//    implementation 'androidx.compose.material:material-icons-extended'
//    implementation 'androidx.activity:activity-compose:1.9.0'
//    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2'
//    implementation 'androidx.lifecycle:lifecycle-runtime-compose:2.8.2'
//    implementation "com.google.accompanist:accompanist-adaptive:0.34.0"
//    implementation "androidx.navigation:navigation-compose:2.7.7"
//
//    ksp 'androidx.lifecycle:lifecycle-compiler:2.8.2'
//
//    def room_version = "2.6.1"
//
//    implementation "androidx.room:room-runtime:$room_version"
//    annotationProcessor "androidx.room:room-compiler:$room_version"
//    ksp "androidx.room:room-compiler:$room_version"
//
//    implementation "io.ktor:ktor-client-core:2.3.8"
//    implementation "io.ktor:ktor-client-okhttp:2.3.8"
//
//    implementation "io.coil-kt:coil-compose:2.6.0"
//
//    implementation "com.google.dagger:hilt-android:2.51.1"
//    ksp "com.google.dagger:hilt-compiler:2.51.1"
//
//    implementation "com.google.android.material:material:1.12.0"
//
//    implementation platform('com.google.firebase:firebase-bom:33.1.1')
//    implementation "com.google.firebase:firebase-analytics-ktx"
//    implementation "com.google.firebase:firebase-crashlytics-ktx"
//    implementation "com.google.firebase:firebase-perf-ktx"
//
//    implementation "com.google.android.gms:play-services-oss-licenses:17.1.0"
//    implementation "com.google.android.gms:play-services-mlkit-face-detection:17.1.0"
//
//    implementation "com.github.clans:fab:1.6.4"
//
//    //noinspection GradleDependency
//    implementation "com.squareup.okhttp3:okhttp:4.12.0"
//
//    implementation "ru.noties.markwon:core:3.1.0"
//
//    implementation "xyz.quaver:documentfilex:0.7.2"
//    implementation "xyz.quaver:floatingsearchview:1.1.7"
//
//    testImplementation "junit:junit:4.13.2"
//    testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0"
//    androidTestImplementation "androidx.test.ext:junit:1.2.1"
//    androidTestImplementation "androidx.test:rules:1.6.1"
//    androidTestImplementation "androidx.test:runner:1.6.1"
//    androidTestImplementation "androidx.test.espresso:espresso-core:3.6.1"
//}