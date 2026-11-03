import java.security.MessageDigest

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
        versionCode = 10
        versionName = "0.3.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    // One APK per ABI plus a universal one, so the 32-bit ARM build can be handed
    // out on its own instead of carrying the 64-bit libraries as dead weight.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Installable by testers, and it upgrades over an existing debug install.
            signingConfig = signingConfigs.getByName("debug")
        }
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

    // The neural voices run on Microsoft's Speech SDK, which ships as a set of shared
    // libraries the bridge dlopen's by path, so they must be extracted to the app's native
    // library folder at install time (the default since Android 6 is to keep them in the APK).
    sourceSets["main"].jniLibs.srcDir(rootProject.file("NeuralSDK/android"))

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libMicrosoft*.so"
        }
    }
}

val stagedVoiceFolders = listOf("VoiceData", "SAPI4Voices", "AnnaVoice", "OneCoreVoice", "NeuralVoices")
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

    // The app verifies the unpacked voice data against this manifest and only re-copies what
    // changed, so the ~160 MB of models is not rewritten on every app update. Written here
    // rather than by a separate task so it can never disagree with what was just staged.
    doLast {
        val dir = destinationDir
        val digest = MessageDigest.getInstance("SHA-256")
        val lines = dir.walkTopDown()
            .filter { it.isFile && it.name != "voice-assets.sha256" }
            .sortedBy { it.relativeTo(dir).invariantSeparatorsPath }
            .map { file ->
                digest.reset()
                file.inputStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                val hex = digest.digest().joinToString("") { String.format("%02x", it) }
                "${file.length()}\t$hex\t${file.relativeTo(dir).invariantSeparatorsPath}"
            }
        dir.resolve("voice-assets.sha256").writeText(lines.joinToString("\n", postfix = "\n"))
    }
}

tasks.named("preBuild").configure { dependsOn(stageVoiceAssets) }
