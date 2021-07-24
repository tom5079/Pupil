plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
    id("com.google.android.gms.oss-licenses-plugin")

    if (File("google-services.json").exists()) {
        println("Firebase Enabled")
        id("com.google.gms.google-services")
        id("com.google.firebase.crashlytics")
        id("com.google.firebase.firebase-perf")
    } else {
        println("Firebase Disabled")
    }
}

android {
    compileSdkVersion(30)
    defaultConfig {
        applicationId = "xyz.quaver.pupil"
        minSdkVersion(21)
        targetSdkVersion(30)
        versionCode = 600
        versionName = "6.0.0-alpha2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("debug") {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            extra.set("enableCrashlytics", false)
            extra.set("alwaysUpdateBuildId", false)
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")

    implementation("io.ktor:ktor-client-core:1.6.1")
    implementation("io.ktor:ktor-client-okhttp:1.6.1")
    implementation("io.ktor:ktor-client-serialization:1.6.1")

    implementation("androidx.appcompat:appcompat:1.3.0")
    implementation("androidx.activity:activity-ktx:1.3.0-rc02")
    implementation("androidx.fragment:fragment-ktx:1.3.6")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.6.0-beta02")

    implementation("org.kodein.di:kodein-di-framework-android-x:7.6.0")

    implementation("com.daimajia.swipelayout:library:1.2.0@aar")

    implementation("com.google.android.material:material:1.4.0")

    implementation(platform("com.google.firebase:firebase-bom:28.3.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")

    implementation("com.google.android.gms:play-services-oss-licenses:17.0.0")

    implementation("com.github.clans:fab:1.6.4")

    //implementation("com.quiph.ui:recyclerviewfastscroller:0.2.1")

    implementation("com.github.piasy:BigImageViewer:1.8.0")
    implementation("com.github.piasy:FrescoImageLoader:1.8.0")
    implementation("com.github.piasy:FrescoImageViewFactory:1.8.0")

    implementation("org.jsoup:jsoup:1.14.1")

    implementation("com.tbuonomo:dotsindicator:4.2")

    //implementation("com.andrognito.patternlockview:patternlockview:1.0.0")
    //implementation("com.andrognito.pinlockview:pinlockview:2.1.0")

    implementation("ru.noties.markwon:core:3.1.0")

    implementation("xyz.quaver:libpupil:2.1.3")
    implementation("xyz.quaver:documentfilex:0.6.1")
    implementation("xyz.quaver:floatingsearchview:1.1.7")

    debugImplementation("com.orhanobut:logger:2.2.0")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.6")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-inline:3.11.2")

    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test:rules:1.4.0")
    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}