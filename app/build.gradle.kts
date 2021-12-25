import com.google.protobuf.gradle.*

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
    id("com.google.android.gms.oss-licenses-plugin")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
    id("com.google.protobuf")
}

android {
    compileSdk = 31
    defaultConfig {
        applicationId = "xyz.quaver.pupil"
        minSdk = 21
        targetSdk = 31
        versionCode = 600
        versionName = "6.0.0-alpha1"
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
            applicationIdSuffix = ".beta"

            isCrunchPngs = false

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.0.5"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
    packagingOptions {
        resources.excludes.addAll(
            listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        )
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2-native-mt")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")

    implementation("androidx.compose.ui:ui:1.0.5")
    implementation("androidx.compose.ui:ui-tooling:1.0.5")
    implementation("androidx.compose.foundation:foundation:1.0.5")
    implementation("androidx.compose.material:material:1.0.5")
    implementation("androidx.compose.material:material-icons-extended:1.0.5")
    implementation("androidx.compose.runtime:runtime-livedata:1.0.5")
    implementation("androidx.compose.ui:ui-util:1.0.5")
    implementation("androidx.compose.animation:animation:1.1.0-rc01")
    implementation("androidx.activity:activity-compose:1.4.0")
    implementation("androidx.navigation:navigation-compose:2.4.0-rc01")

    implementation("com.google.accompanist:accompanist-flowlayout:0.20.3")
    implementation("com.google.accompanist:accompanist-appcompat-theme:0.20.3")
    implementation("com.google.accompanist:accompanist-insets:0.20.3")
    implementation("com.google.accompanist:accompanist-insets-ui:0.20.3")
    implementation("com.google.accompanist:accompanist-drawablepainter:0.20.3")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.20.3")

    implementation("io.coil-kt:coil-compose:1.4.0")

    implementation("io.ktor:ktor-client-core:1.6.7")
    implementation("io.ktor:ktor-client-okhttp:1.6.7")
    implementation("io.ktor:ktor-client-serialization:1.6.7")

    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("androidx.activity:activity-ktx:1.4.0")
    implementation("androidx.fragment:fragment-ktx:1.4.0")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.2")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.7.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.4.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.4.0")

    implementation("androidx.room:room-runtime:2.4.0")
    annotationProcessor("androidx.room:room-compiler:2.4.0")
    kapt("androidx.room:room-compiler:2.4.0")
    implementation("androidx.room:room-ktx:2.4.0")

    implementation("androidx.datastore:datastore:1.0.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation("org.kodein.di:kodein-di-framework-compose:7.10.0")

    implementation("com.google.android.material:material:1.4.0")

    implementation(platform("com.google.firebase:firebase-bom:29.0.3"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")

    implementation("com.google.protobuf:protobuf-javalite:3.19.1")

    implementation("com.google.android.gms:play-services-oss-licenses:17.0.0")

    implementation("org.jsoup:jsoup:1.14.3")

    implementation("ru.noties.markwon:core:3.1.0")

    implementation("xyz.quaver:documentfilex:0.7.1")
    implementation("xyz.quaver:subsampledimage:0.0.1-alpha16-SNAPSHOT")

    implementation("com.google.guava:guava:31.0.1-jre")

    implementation("org.kodein.log:kodein-log:0.11.1")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-inline:4.2.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test:rules:1.4.0")
    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.0.5")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.19.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("java") {
                    option("lite")
                }
            }
        }
    }
}

task<Exec>("clearAppCache") {
    commandLine("adb", "shell", "pm", "clear", "xyz.quaver.pupil.debug")
}