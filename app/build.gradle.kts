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
        versionName = VERSION
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
//            isMinifyEnabled = true
//            isShrinkResources = true
            applicationIdSuffix = ".beta"

            isCrunchPngs = false

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = File("/tmp/keystore.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Versions.JETPACK_COMPOSE
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

    implementation("androidx.activity:activity-compose:1.4.0")
    implementation("androidx.navigation:navigation-compose:2.5.0-alpha03")

    implementation(JetpackCompose.FOUNDATION)
    implementation(JetpackCompose.UI)
    implementation(JetpackCompose.UI_UTIL)
    implementation(JetpackCompose.UI_TOOLING)
    implementation(JetpackCompose.ANIMATION)
    implementation(JetpackCompose.MATERIAL)
    implementation(JetpackCompose.MATERIAL_ICONS)
    implementation(JetpackCompose.RUNTIME_LIVEDATA)

    implementation(Accompanist.INSETS)
    implementation(Accompanist.INSETS_UI)
    implementation(Accompanist.FLOW_LAYOUT)
    implementation(Accompanist.SYSTEM_UI_CONTROLLER)
    implementation(Accompanist.DRAWABLE_PAINTER)
    implementation(Accompanist.APPCOMPAT_THEME)

    implementation("io.coil-kt:coil-compose:1.4.0")

    implementation("io.ktor:ktor-client-core:1.6.8")
    implementation("io.ktor:ktor-client-okhttp:1.6.8")
    implementation("io.ktor:ktor-client-serialization:1.6.8")

    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("androidx.activity:activity-ktx:1.4.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.7.1")

    implementation("androidx.room:room-runtime:2.4.2")
    annotationProcessor("androidx.room:room-compiler:2.4.2")
    kapt("androidx.room:room-compiler:2.4.2")
    implementation("androidx.room:room-ktx:2.4.2")

    implementation("androidx.datastore:datastore:1.0.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation("org.kodein.di:kodein-di-framework-compose:7.11.0")

    implementation("com.google.android.material:material:1.5.0")

    implementation(platform("com.google.firebase:firebase-bom:29.0.3"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")

    implementation("com.google.protobuf:protobuf-javalite:3.19.1")

    implementation("com.google.android.gms:play-services-oss-licenses:17.0.0")

    implementation("org.jsoup:jsoup:1.14.3")

    implementation("ru.noties.markwon:core:3.1.0")

    implementation("xyz.quaver.pupil.sources:core:0.0.1-alpha01-DEV25")

    implementation("xyz.quaver:documentfilex:0.7.2")
    implementation("xyz.quaver:subsampledimage:0.0.1-alpha19-SNAPSHOT")

    implementation("com.google.guava:guava:31.1-jre")

    implementation("org.kodein.log:kodein-log:0.12.0")
//    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-inline:4.4.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test:rules:1.4.0")
    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.1.1")
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