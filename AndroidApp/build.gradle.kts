plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.quinton.sandroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quinton.sandroid"
        minSdk = 26
        targetSdk = 35
        ndkVersion = "27.2.12479018"
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/sandroidAssets"))
}

val stagedVoiceFolders = listOf("VoiceData", "SAPI4Voices", "AnnaVoice", "OneCoreVoice")
val stageVoiceAssets by tasks.registering(Copy::class) {
    stagedVoiceFolders.forEach { folder ->
        val source = rootProject.file(folder)
        if (source.exists()) from(source) { into(folder) }
    }
    from(rootProject.file("TruVoiceData")) {
        include("*.s")
        into("TruVoiceData")
    }
    into(layout.buildDirectory.dir("generated/sandroidAssets"))
}
tasks.named("preBuild").configure { dependsOn(stageVoiceAssets) }
