# Vaiwu Android

Android client for the [Vaiwu](https://github.com/zimin3d/vaiwu) virtual pet.
The pet simulation runs in C++ from the `external/vaiwu` submodule and is
exposed to Kotlin through JNI.

## Requirements

- JDK 17
- Android SDK 35 and NDK `27.0.12077973`
- CMake 3.22.1 (installed by the Android SDK)

## Checkout

The submodule is fetched over HTTPS:

```sh
git clone --recurse-submodules https://github.com/zimin3d/vaiwu-android.git
```

If you already cloned without submodules:

```sh
git submodule update --init --recursive
```

SSH users can rewrite the URL locally instead of editing `.gitmodules`:

```sh
git config url."git@github.com:".insteadOf "https://github.com/"
```

## Build

```sh
./gradlew assembleDebug
```

Release builds are produced by the `Android APK` workflow. Tagged builds are
signed and published to GitHub Releases; untagged builds are debug-signed.

## Tests

```sh
./gradlew :app:testDebugUnitTest
```

## Dependency integrity

Dependencies are locked (`app/gradle.lockfile`) and verified against
`gradle/verification-metadata.xml`. When a dependency needs to be updated:

1. Run the `Refresh dependency locks` workflow manually, or
2. Run locally:
   ```sh
   ./gradlew --write-locks --write-verification-metadata sha256 \
     :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
   ```
3. Commit `app/gradle.lockfile` and `gradle/verification-metadata.xml`.

Dependabot builds use lenient verification so a version bump does not fail
before the metadata refresh workflow runs. Unit-test configurations are
excluded from dependency locking.

## Native notes

The `vaiwu_native` library is linked with 16 KB page-size options, as required
for Android 15+ devices and for Play updates targeting SDK 35 from
February 2027.

## Licensing

See [NOTICE](NOTICE). The bundled Vaiwu firmware is MIT-licensed.
