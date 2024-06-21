import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.impldep.org.apache.commons.lang.mutable.Mutable

/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.parcelize")    
    alias(libs.plugins.ksp)
}

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        //targetSdk = 34

        applicationId = "org.readium.r2reader"

        versionName = "3.0.0-beta.1"
        versionCode = 300000

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk.abiFilters.add("armeabi-v7a")
        ndk.abiFilters.add("arm64-v8a")
        ndk.abiFilters.add("x86")
        ndk.abiFilters.add("x86_64")
        //testInstrumentationRunnerArguments["runnerBuiler"]= "de.mannodermaus.junit5.AndroidJUnit5Builder"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"))
        }
        getByName("debug") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"))
        }
    }
    packaging {
        resources.excludes.add("META-INF/*")
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            res.srcDirs("src/main/res")
            assets.srcDirs("src/main/assets")
        }
    }
    namespace = "org.readium.r2.testapp"
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.legacy.v4)

    implementation(project(":readium:readium-shared"))
    implementation(project(":readium:readium-streamer"))
    implementation(project(":readium:readium-navigator"))
    implementation(project(":readium:navigators:media:readium-navigator-media-audio"))
    implementation(project(":readium:navigators:media:readium-navigator-media-tts"))
    // Only required if you want to support audiobooks using ExoPlayer.
    implementation(project(":readium:adapters:exoplayer"))
    implementation(project(":readium:readium-navigator-media2"))
    implementation(project(":readium:readium-opds"))
    implementation(project(":readium:readium-lcp"))
    // Only required if you want to support PDF files using PDFium.
    implementation(project(":readium:adapters:pdfium"))

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.cardview)

    implementation(libs.bundles.compose)
//    debugImplementation(libs.androidx.compose.ui)

    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.google.material)
    implementation(libs.timber)
    implementation(libs.picasso)
    implementation(libs.joda.time)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)

    implementation(libs.bundles.media3)

    // Room database
    implementation(libs.bundles.room)
    implementation(libs.junit)
    //implementation(libs.junit.jupiter)
    implementation(libs.androidx.junit.ktx)
    ksp(libs.androidx.room.compiler)

    // Tests
    //testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.ext.junit)
    androidTestImplementation(libs.androidx.expresso.core)
    androidTestImplementation(libs.robolectric)
    //androidTestImplementation(libs.junit.jupiter)
    androidTestImplementation(libs.kotlin.test)
    //androidTestImplementation(libs.testcore)
    //androidTestImplementation(libs.androidx.junit5.test)
    //androidTestImplementation(libs.junit5.test.core)
    //androidTestRuntimeOnly(libs.junit5.test.runner)
    //androidTestRuntimeOnly(libs.junit.jupiter.engine)
    androidTestImplementation(libs.robolectric)
    testImplementation(libs.robolectric)
    //testImplementation(libs.kotlin.junit)
    //testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.junit)
    //testImplementation(libs.kotlin.test)
    //testImplementation(platform(libs.junit.junitbom))
    //testImplementation(libs.testcore)
    //testImplementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    //testImplementation(libs.androidx.junit5.test)
    //testImplementation(libs.junit.platform)
    //testCompileOnly(libs.junit.jupiter)
}

/*tasks.withType<Test>{
    useJUnitPlatform()
}*/

