# Sapple

The Microsoft-era speech voices, from SAM through the Windows 11 natural voices, as system voices on iPhone, iPad and Mac.

## About

Sapple puts the old and not-so-old Windows voices on Apple devices as real system voices, so they work with VoiceOver, Spoken Content and any app that uses the system speech voices. That covers the SAPI 4 and SAPI 5 voices (Sam, Mike, Mary and the rest), the TruVoice voices from Microsoft Agent, Anna from Windows Vista and 7, the Windows 10 and 11 OneCore voices, and the Windows 11 natural voices. Everything runs on the device and works offline.

Most of the engines are C reconstructions of the original Windows engines, which their authors checked against the originals down to the sample. The natural voices run on Microsoft's own Speech SDK. The voice data itself is not in this repository (see About the voices).

## Just want to use it?

Ready to install builds are on the downloads page: https://quintonwilliams.me/sapple/

- iPhone and iPad: [Sapple-1.1.1.ipa](https://quintonwilliams.me/sapple/Sapple-1.1.1.ipa)
- Mac, Apple Silicon only: [Sapple-1.1.1-mac.zip](https://quintonwilliams.me/sapple/Sapple-1.1.1-mac.zip)
- AltStore source: `https://quintonwilliams.me/sapple/source.json`

The downloads page lists each file's size and SHA-256.

These builds include voice data that is not part of this repository's code and belongs to its owners: Microsoft (the SAPI 4 and SAPI 5 voices, Anna, the OneCore voices, and the natural voices with the Speech SDK they run on) and Centigram Communications, whose TruVoice later passed to Lernout & Hauspie, ScanSoft and Nuance (the TruVoice tables). It's included for personal use, isn't covered by Sapple's licence, and will be removed if a rights holder asks.

AltStore, using the source (stays installed, refreshes itself over Wi-Fi, and shows updates):

1. Install AltServer on your Mac or PC from [altstore.io](https://altstore.io/), then use it to put AltStore on your iPhone.
2. In AltStore, go to the Sources screen and tap the plus button.
3. Paste `https://quintonwilliams.me/sapple/source.json` and add it.
4. Open the Sapple source, tap Free or Get next to Sapple, and sign in with your Apple ID if asked.
5. Open Sapple once. Keep AltServer running so AltStore can refresh the app before it expires.

AltStore also installs the IPA directly: download it, then in My Apps tap the plus button and pick the file.

SideStore reads the same source. Add it in SideStore the same way. SideStore refreshes apps on the phone itself, so after its one time setup you don't need a computer to keep Sapple running.

Sideloadly (quick one time install):

1. Install Sideloadly on your Mac or PC from [sideloadly.io](https://sideloadly.io/).
2. Connect your iPhone with a cable and open Sideloadly.
3. Drag the IPA in, enter your Apple ID, and click Start.
4. On your iPhone, open Settings, then General, then VPN and Device Management, and trust your developer profile.
5. Open Sapple once.

With a free Apple ID the app stops working after 7 days and has to be reinstalled. A paid Apple Developer account keeps it running for a year.

On the Mac, the download is signed and notarized. Unzip it, put Sapple in Applications and open it once so macOS picks up the voices. Leave it there, since macOS finds the voices through the app. The Mac app needs an Apple Silicon Mac; it does not run on an Intel Mac.

## Picking the voices

On iPhone and iPad, go to Settings, Accessibility, VoiceOver, Speech, Voice (or Settings, Accessibility, Spoken Content, Voices) and look under English for the Sapple voices. You can also add them to the VoiceOver rotor from the same Speech screen.

On the Mac, the voices show up in VoiceOver Utility under Speech, and in System Settings, Accessibility, Spoken Content, System voice.

The Sapple app itself lets you preview every voice and set the speed, and has a singing mode for Sam, Mike and Mary.

## Building from source

You need a Mac with Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`). The Xcode project is generated from `project.yml`. The app targets iOS 18 and macOS 15 and up.

The voice data isn't included, so first put your own copies in these folders at the top of the repo. They're all ignored by git.

```
VoiceData/      SAPI 5: Sam, Mike, Mary (.spd, .sdf) and the two .LXA lexicons
SAPI4Voices/    SAPI 4: msttssyn.dll and its .vce / .cfg voice files    (make -C SAPI4 bundle)
TruVoiceData/   tvdata.s, generated from the TruVoice engine tables        (make truvoice-data)
AnnaVoice/      Anna: the eleven M1033DSK.* files from Windows 7            (make anna-data)
OneCoreVoice/   Windows 10 / 11 OneCore voices and language data            (make onecore-data; the en-AU / en-CA ones:
                tools/onecore_lof_stage.py, from the Windows 11 Languages and Optional Features image)
NeuralVoices/   Windows 11 natural voice packages and their model key       (make neural-stage)
NeuralSDK/      Microsoft's embedded Speech SDK libraries, ios/ and macos/  (make neural-stage)
```

The make targets stage or check each folder against the SHA-256 lists in `tools/` and `SAPI4/tools/`, so you can tell whether your copy matches the one the engines were checked against.

Then:

```
xcodegen generate
xcodebuild -project ClassicVoices.xcodeproj -scheme ClassicVoices -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath build -allowProvisioningUpdates \
  DEVELOPMENT_TEAM=<your team ID> build
```

To sign with your own account, change `DEVELOPMENT_TEAM`, the bundle identifiers in `project.yml` and the app group in the entitlements files. The Mac targets (`ClassicVoices-macOS`) sign with a Developer ID certificate; the comments in `project.yml` explain.

To check the code compiles without any voice data, run `tools/stub_data.sh` first. It fills the folders with placeholders (empty folders, zeroed TruVoice tables, empty libraries). That build can't speak, so don't install it. `tools/stub_data.sh --clean` removes the placeholders.

The Makefile also builds each engine for the Mac with sanitizers and renders samples, for example `make test`, `make anna-test`, `make onecore-test`, `make truvoice-test`, `make sapi4-test` and `make neural-test`.

The build produces Sapple.app on iOS and macOS. Inside the code the project, its targets and the extension (ClassicVoicesExtension.appex) are still called ClassicVoices, and the bundle identifiers are unchanged.

## Android (early port)

The Android project is in `AndroidApp/` (kept separate from the Apple `App/` source folder) and is called Sandroid. It provides a system Text-to-Speech service plus a settings screen for picking the voice, speed and pitch. It targets Android 8 (API 26) and builds `armeabi-v7a` (ARM32), `arm64-v8a` (ARM64), and `x86_64` native libraries. Experimental offline natural voices use Microsoft's Android embedded Speech SDK 1.33.0: Jenny, Aria, Guy, Sonia, Ryan, Neerja, and Prabhat. The SDK's four required native libraries are packaged for every selected ABI, including ARM32.

Open this repository's `sandroid` directory in Android Studio with JDK 17, Android SDK platform 35, Build Tools 34.0.0, NDK 27.2.12479018, and CMake 3.22.1. The included Gradle wrapper downloads Gradle 8.7; a separate Gradle installation is not required. The Gradle build copies any staged data folders into the app assets; the app installs those assets into its private files directory on first use. Stage the same local, gitignored folders described above (`VoiceData`, `SAPI4Voices`, `AnnaVoice`, `OneCoreVoice`, and generated `TruVoiceData/tvdata.s`). Voice data and engine tables are not included in this repository. Without TruVoice tables, the Android build uses a non-speaking TruVoice placeholder; the other engines can still use their staged voice data.

For a command-line debug build, set `JAVA_HOME` to your JDK 17 installation and `ANDROID_HOME` to your Android SDK directory (or set `sdk.dir` in a local, gitignored `local.properties` file), then run:

```sh
./gradlew :androidApp:assembleDebug
```

The installable, debug-signed APK is `AndroidApp/build/outputs/apk/debug/androidApp-debug.apk`. The default build is universal: Android 8 and later on ARM32, ARM64, and x86-64 devices. No release signing key is needed for this debug build. A build without any staged voice data will have no usable voices.

Use the build script to create named APKs in the root `build/` folder:

```sh
./tools/build-android.sh arm32      # armeabi-v7a only
./tools/build-android.sh arm64      # arm64-v8a only
./tools/build-android.sh universal  # ARM32 + ARM64 + x86-64 (also the default)
./tools/build-android.sh all        # produce all three APKs
```

Outputs are named `build/Sandroid-<versionName>-<target>.apk`. These commands use the same debug signing key as previous test builds, so they can update those installations. The script finds Homebrew's JDK 17 on macOS when `JAVA_HOME` isn't set. The SDK/NDK and staged voice data requirements above still apply. All variants include the same voice assets; choosing one ABI reduces native-library size. Use the universal APK when unsure which architecture a device's Android installation supports. For example, an ARM64 processor can run a 32-bit Android installation; the Android ABI matters more than the processor specification. Check a connected device with `adb shell getprop ro.product.cpu.abilist`.

The APK includes a size/SHA-256 manifest for its staged voice files. On the first speech request in each app process, the installer verifies existing files and atomically replaces missing, incomplete, outdated, or corrupted copies from the APK. This also repairs partial nonempty files left by older builds without uninstalling or clearing settings. Copy failures are reported rather than silently retaining unusable data. Subsequent requests reuse the verified installation in that process.

For direct Gradle builds, select ABIs with `-PsandroidAbis=armeabi-v7a`, `-PsandroidAbis=arm64-v8a`, or a comma-separated combination. Omitting the property builds all three supported ABIs. Architecture-specific APKs are intended for direct installation; use the universal build when both ARM widths are needed. ARM32 builds still require testing on a physical 32-bit device; the existing ARM64 emulator results below do not establish ARM32 runtime compatibility.

For a remote speech failure, reproduce it using Preview, reopen Sandroid if it closes, and press **Copy diagnostics**. Paste the report into the bug report. It includes the app/process architecture, the last speech operation, memory availability, and recent process exit reasons. On Android 12 and later, available native crash tombstones are decoded into the signal and crashing thread's backtrace. Reports are collected locally and copied only when the button is pressed. No speech text is recorded.

The instrumentation runner also accepts `-e oneCoreOnly true` to check PCM from all thirteen OneCore voices plus Eva cancellation and recovery, using the actual installed app ABI:

```sh
./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest -PsandroidAbis=armeabi-v7a
adb install -r AndroidApp/build/outputs/apk/debug/androidApp-debug.apk
adb install -r AndroidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb shell am instrument -w -e oneCoreOnly true com.quinton.sandroid.test/com.quinton.sandroid.NaturalVoiceInstrumentation
```

If you have the supported Sapple Mac app, its resource folders can supply `VoiceData`, `SAPI4Voices`, `AnnaVoice`, `OneCoreVoice`, and `NeuralVoices`. Look inside `Contents/PlugIns/ClassicVoicesExtension.appex/Contents/Resources/`. Copy each entire folder to the repository root, including `NeuralVoices/model.key`; natural voices also need the shared files in `OneCoreVoice`. Gradle downloads the Android SDK from Maven Central and packages its core, embedded TTS, TTS runtime, and ONNX runtime libraries. The Java/cloud API and telemetry library are not packaged, and the app has no internet permission. The Mac SDK dylibs are not used on Android.

TruVoice's tables are embedded in the Mac executables rather than a resource folder. With `Sapple.app` copied into the repository, recover them using `python3 tools/truvoice_from_sapple.py Sapple.app`, then rebuild the APK. This script requires matching table bytes and symbol offsets in the app and extension and rejects unsupported data images. The recovered assembly preserves the data layout but has different text from the upstream assembly checked by `make truvoice-data`. The copied app and recovered voice data are gitignored.

The natural-voice build passed tests on an Android 15 ARM64 emulator with 4 KB memory pages: all seven voices produced nonzero PCM, canceled speech and spoke again, and Android's TTS service registered the voices as offline and synthesized a WAV. Physical Pixel testing is still required. SDK 1.33's libraries have 4 KB ELF alignment and do not provide native 16 KB page-size support. Our own native library is aligned for 16 KB pages. SDK 1.44.0 and 1.52.0 have newer page-size support but canceled synthesis with these models in emulator tests; changing SDK versions is not a verified fix for these voice packages. An alternative SDK can be selected for experiments with `-PembeddedSpeechVersion=1.52.0`.

To repeat the natural-voice smoke test on a connected test device:

```sh
./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
adb install -r AndroidApp/build/outputs/apk/debug/androidApp-debug.apk
adb install -r AndroidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb shell am instrument -w com.quinton.sandroid.test/com.quinton.sandroid.NaturalVoiceInstrumentation
```

The runner prints a `FAILED` message on an assertion failure, or a `Process crashed` result for a native crash; check its output rather than relying solely on adb's exit status. Use `adb shell getconf PAGE_SIZE` to check the device's memory page size.

After installing Sandroid, open it (or tap the gear beside Sandroid in Android's text-to-speech settings). Choose an engine first, then a voice from that engine's filtered list, and set the speed, pitch and volume; OK applies them to every app on the device. Anna appears under SAPI 5 and keeps its own synthesis backend and voice data. Volume ranges from 0% (mute) to 200%, with 100% preserving the original audio; the same gain applies to previews and system speech for every voice. Boosted peaks are capped to the PCM range and can distort at high settings. Reopening settings restores the engine for the saved voice. Switching engines remembers each engine's last selected voice while the screen is open, and Cancel leaves the saved settings unchanged. Only voices whose data was staged into the build are offered, so the list matches what can actually speak. The Android service synthesizes plain text through the native C engines; the iOS SSML segmentation and pause behavior has not been ported yet.

To check Anna grouping and selection restoration, volume defaults and save/cancel behavior, PCM gain limits, and saved volume in system speech, run the instrumentation command above with `-e settingsOnly true` before the component name. This test requires staged Anna data and at least one other voice group, and restores the saved settings afterward.

Engine and voice pickers open in separate scrollable dialogs so earlier voices remain reachable when reopening on the final selection. The settings page scrolls independently, with system-bar padding on its viewport. To test touch scrolling from the last voice to the first and back in every available group, plus reaching the top and bottom of settings, run the instrumentation command with `-e scrollingOnly true`. Repeat with a small display and larger system font to check constrained layouts.

Preview playback waits for all queued audio frames before releasing the output track, including a silent tail buffer for short clips and the output pipeline. Starting another preview or leaving the screen cancels playback immediately; the playback worker owns releasing its track. To check short and buffered playback completion and cancellation on a test device, build/install both APKs as above and run the instrumentation command with `-e previewOnly true` before the component name.

## Releasing

One command builds and publishes a release: the IPA, the notarized Mac zip, the AltStore / SideStore source and the downloads page.

```
make release VERSION=1.1 NOTES="What's new in this version." DRYRUN=1   # build and show the changes, publish nothing
make release VERSION=1.1 NOTES="What's new in this version."            # the real thing
```

It needs every voice data folder, and the server and notarization settings in `tools/release.local` (the list is at the top of `tools/release.sh`). It sets the version in `project.yml` and the next build number, builds both apps, reads the version back out of the built apps, adds the new entry to the top of `source.json`, updates the page, backs both up on the server before replacing them, and checks the live source and the IPA download afterwards. It refuses a version that is already in the source. AltStore and SideStore show the update the next time they refresh the source. Commit `project.yml` and `README.md` afterwards.

Mac updates through Sparkle aren't set up yet; Mac users download the new zip from the page.

## Project layout

```
App/          the app: voice list, preview, speed, singing, self-test
Extension/    the speech synthesis extension that registers the voices with the system
Shared/       code both use: voice list, SSML, rate and pitch, one C bridge per engine
Engine/       the engines, vendored from their upstream projects, with our patches in Engine/patches/
SAPI4/        an x86 interpreter that runs the original SAPI 4 DLLs, kept as the reference for testing
Tests/        SSML and pause tests
tools/        data staging, hash checks, table and patch generators
```

## About the voices

- SAM, Mike and Mary (SAPI 5): engine from [ms-sam-mike-mary-decomp](https://github.com/KamiKitsune420/ms-sam-mike-mary-decomp) by KamiKitsune420. Voice data by Microsoft.
- The SAPI 4 voices: Microsoft's 1999 engine `msttssyn.dll`, decompiled to C in a separate project of mine, sapi4-decomp. The engine still reads its data from the original DLL and voice files, which are Microsoft's.
- TruVoice (the Microsoft Agent voices): engine from [OpenTV](https://github.com/RetroBunn/tv-decomp) by RetroBunn. The engine tables are Centigram's, whose TruVoice business went to Lernout & Hauspie and later ScanSoft / Nuance.
- Anna: engine from [ms-ana-decomp](https://github.com/KamiKitsune420/ms-ana-decomp) by KamiKitsune420. Voice data by Microsoft.
- David, Zira, Mark, Hazel, George and Susan (OneCore): engine from [ms-david-zira-decomp](https://github.com/KamiKitsune420/ms-david-zira-decomp) by KamiKitsune420, plus Eva, Sarah, Matilda, and the Australian and Canadian voices Catherine, James, Linda and Richard, which I added. Voice data by Microsoft.
- Jenny, Aria, Guy, Sonia and Ryan (Windows 11 natural voices), and Neerja and Prabhat (Indian English natural voices): these run on Microsoft's embedded Speech SDK. Voices and SDK by Microsoft.

None of the voice data, original engine files or SDKs are in this repo, and this project doesn't license them. They belong to their owners. The pre-built downloads on quintonwilliams.me do bundle them, for personal use, and they will be removed if a rights holder asks.

## License

No license has been picked for Sapple's own code yet, so for now it's all rights reserved. The vendored engines keep their own licenses (MIT for the four from GitHub; sapi4-decomp has none yet). NOTICE has the details, including the pinned upstream commits.
