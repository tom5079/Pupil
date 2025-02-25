plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.gms.oss.licenses)
    alias(libs.plugins.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    id("kotlin-parcelize")
}

android {
    namespace = "xyz.quaver.pupil"
    compileSdk = 35

    defaultConfig {
        applicationId = "xyz.quaver.pupil"
        minSdk = 16
        targetSdk = 35
        versionCode = 70
        versionName = "5.3.16"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    buildTypes {
        debug {
            defaultConfig.minSdk = 21

            isMinifyEnabled = false
            isShrinkResources = false

            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"

            extra.apply {
                set("enableCrashlytics", false)
                set("alwaysUpdateBuildId", false)
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        viewBinding = true
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.androidx.compose.runtime)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.preference.ktx)
    implementation(libs.recyclerview)
    implementation(libs.constraintlayout)
    implementation(libs.gridlayout)
    implementation(libs.biometric)
    implementation(libs.work.runtime.ktx)

    implementation(libs.library)

    implementation(libs.material)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.perf.ktx)

    implementation(libs.play.services.oss.licenses)
    implementation(libs.play.services.mlkit.face.detection)

    implementation(libs.fab)

    implementation(libs.bigimageviewer)
    implementation(libs.frescoimageloader)
    implementation(libs.frescoimageviewfactory)
    implementation(libs.imagepipeline.okhttp3)

    //noinspection GradleDependency
    implementation(libs.okhttp)
    implementation(libs.ktor.network)

    implementation(libs.dotsindicator)

    implementation(libs.pinlockview)
    implementation(libs.patternlockview)

    implementation(libs.core)

    implementation(libs.ripplebackground.library)
    implementation(libs.recyclerview.fastscroller)

    implementation(libs.jsoup)

    implementation(libs.documentfilex)
    implementation(libs.floatingsearchview)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.rules)
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.espresso.core)
}