import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "su.neit.vaiwu"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "su.neit.vaiwu"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
                abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
    }

    signingConfigs {
        if (!System.getenv("KEYSTORE_FILE").isNullOrEmpty()) {
            create("release") {
                storeFile = file(System.getenv("KEYSTORE_FILE"))
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEYSTORE_ALIAS") ?: "filament"
                keyPassword = System.getenv("KEYSTORE_KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            val isCi = System.getenv("CI") == "true"
            val isTag = System.getenv("GITHUB_REF_TYPE") == "tag"
            if (keystoreFile.isNullOrEmpty()) {
                if (isCi && isTag) {
                    throw GradleException("Release builds for tagged releases on CI must configure KEYSTORE_FILE.")
                }
                logger.warn("KEYSTORE_FILE is not set: release build will use debug signing")
            }
            signingConfig = signingConfigs.getByName(
                if (keystoreFile.isNullOrEmpty()) "debug" else "release",
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            // Ассеты живут в репозитории приложения (app/src/main/assets);
            // ESP32-ассеты остаются в внешнем репозитории (external/vaiwu).
            assets.srcDir("src/main/assets")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
