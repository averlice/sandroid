import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val supportedAbis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")
val selectedAbis = providers.gradleProperty("sandroidAbis").orNull
    ?.split(',')?.map { it.trim() }?.distinct() ?: supportedAbis
require(selectedAbis.isNotEmpty() && selectedAbis.all { it in supportedAbis }) {
    "sandroidAbis must be a comma-separated subset of ${supportedAbis.joinToString()}."
}

android {
    namespace = "com.quinton.sandroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quinton.sandroid"
        minSdk = 26
        targetSdk = 35
        ndkVersion = "27.2.12479018"
        versionCode = 9
        versionName = "0.2.7"
        testInstrumentationRunner = "com.quinton.sandroid.NaturalVoiceInstrumentation"
        ndk { abiFilters += selectedAbis }
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
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/speechJni"))
    // The native bridge and the SDK locate runtime extensions by their filesystem path.
    packaging { jniLibs { useLegacyPackaging = true } }
}

val embeddedSpeechSdk by configurations.creating
val embeddedSpeechVersion = providers.gradleProperty("embeddedSpeechVersion").getOrElse("1.33.0")
dependencies {
    // Only extract native offline TTS libraries; the Java/cloud SDK is not used.
    add(embeddedSpeechSdk.name, "com.microsoft.cognitiveservices.speech:client-sdk-embedded:$embeddedSpeechVersion@aar")
}
val stageSpeechJni by tasks.registering(Sync::class) {
    val libraries = listOf("core", "extension.embedded.tts", "extension.embedded.tts.runtime", "extension.onnxruntime")
    inputs.property("libraries", libraries)
    inputs.property("abis", selectedAbis)
    from({ zipTree(embeddedSpeechSdk.singleFile) }) {
        include(libraries.flatMap { library ->
            selectedAbis.map { abi ->
                "jni/$abi/libMicrosoft.CognitiveServices.Speech.$library.so"
            }
        })
        includeEmptyDirs = false
        eachFile { path = path.removePrefix("jni/") }
    }
    into(layout.buildDirectory.dir("generated/speechJni"))
}

val stagedVoiceFolders = listOf("VoiceData", "SAPI4Voices", "AnnaVoice", "OneCoreVoice", "NeuralVoices")
val stageVoiceAssets by tasks.registering(Sync::class) {
    stagedVoiceFolders.forEach { folder ->
        val source = rootProject.file(folder)
        if (source.exists()) from(source) { into(folder) }
    }
    from(rootProject.file("TruVoiceData")) {
        include("*.s")
        into("TruVoiceData")
    }
    into(layout.buildDirectory.dir("generated/sandroidAssets"))
    doLast {
        val root = destinationDir
        val manifest = root.resolve("voice-assets.sha256")
        val entries = root.walkTopDown().filter { it.isFile && it != manifest }.sortedBy { it.path }.map { asset ->
            val digest = MessageDigest.getInstance("SHA-256")
            asset.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            "${asset.length()}\t$hash\t${asset.relativeTo(root).invariantSeparatorsPath}"
        }.toList()
        manifest.writeText(entries.joinToString("\n", postfix = "\n"))
    }
}
tasks.named("preBuild").configure { dependsOn(stageVoiceAssets, stageSpeechJni) }
