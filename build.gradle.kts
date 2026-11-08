import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application") version "9.4.0"
}

android {
    val releaseSigningPropertiesFile = rootProject.file("release-signing.properties")
    val releaseBuildRequested = gradle.startParameter.taskNames.any {
        it.contains("release", ignoreCase = true)
    }

    namespace = "com.valvesoftware.source"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.valvesoftware.source"
        minSdk = 29
        ndk { abiFilters += "arm64-v8a" }
        targetSdk = 29
        versionCode = 1170044
        versionName = "1.17.40-dev16"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            check(!releaseBuildRequested || releaseSigningPropertiesFile.isFile) {
                "Missing release-signing.properties. Release builds require the dedicated signing certificate."
            }
            if (releaseSigningPropertiesFile.isFile) {
                val signingProperties = Properties().apply {
                    releaseSigningPropertiesFile.inputStream().use(::load)
                }
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("src/org/libsdl"))
            kotlin.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
            assets.setSrcDirs(listOf("assets"))
            jniLibs.setSrcDirs(listOf("lib"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        buildConfig = false
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        jniLibs.excludes += setOf("**/libservo_capi.so", "**/libhtml_cefrium.so", "**/libcef.so")
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.noties.markwon:core:4.6.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}
