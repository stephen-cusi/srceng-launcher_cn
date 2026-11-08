import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application") version "9.4.0"
    id("com.cefrium") version "0.17.1"
}

val hl2sbCefrium = providers.gradleProperty("hl2sbCefrium").orElse("false").get().toBoolean()
val cefriumNativeDir = providers.gradleProperty("cefriumNativeDir").orNull
if (hl2sbCefrium) {
    check(cefriumNativeDir != null && file("$cefriumNativeDir/arm64-v8a/libhtml_cefrium.so").isFile) {
        "The optional Cefrium bridge requires -PcefriumNativeDir=<external jniLibs directory>"
    }
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
        manifestPlaceholders["hl2sbCefrium"] = hl2sbCefrium.toString()
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
            java.setSrcDirs(listOf("src"))
            kotlin.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
            assets.setSrcDirs(listOf("assets"))
            jniLibs.setSrcDirs(listOf("lib") + listOfNotNull(cefriumNativeDir))
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
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation("com.cefrium:cefrium-sdk:0.17.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.noties.markwon:core:4.6.2")
}

// The generated native services do not have Java class counterparts.
tasks.matching { it.name.startsWith("generateCefriumJavalessManifest") }.configureEach {
    doLast {
        outputs.files.files.filter { it.name == "AndroidManifest.xml" }.forEach { file ->
            val manifest = file.readText()
                .replace("<manifest xmlns:android=", "<manifest xmlns:tools=\"http://schemas.android.com/tools\" xmlns:android=")
                .replace("android:nativeService=\"true\"", "android:nativeService=\"true\" tools:ignore=\"MissingClass\"")
            file.writeText(manifest)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}
