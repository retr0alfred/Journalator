import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing is driven entirely by the environment (GitHub Actions secrets).
 * When the secrets are absent — a fresh clone, a local build — the signing config is
 * simply not created and the release variant builds unsigned. A clone must always build.
 */
val keystoreBase64: String? = System.getenv("KEYSTORE_BASE64")
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val keyAlias: String? = System.getenv("KEY_ALIAS")
val keyPasswordEnv: String? = System.getenv("KEY_PASSWORD")
val hasSigningSecrets = !keystoreBase64.isNullOrBlank() &&
    !keystorePassword.isNullOrBlank() &&
    !keyAlias.isNullOrBlank() &&
    !keyPasswordEnv.isNullOrBlank()

val decodedKeystore = layout.buildDirectory.file("signing/release.jks").get().asFile

android {
    namespace = "dev.retr0alfred.journalator"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.retr0alfred.journalator"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only English ships. localeConfig keeps per-app language switching available
        // for anyone who adds a translation later, without touching code.
        @Suppress("DEPRECATION")
        resourceConfigurations += listOf("en")

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.generateKotlin", "true")
        }
    }

    if (hasSigningSecrets) {
        signingConfigs {
            create("release") {
                decodedKeystore.parentFile.mkdirs()
                decodedKeystore.writeBytes(Base64.getDecoder().decode(keystoreBase64))
                storeFile = decodedKeystore
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                keyPassword = keyPasswordEnv
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigningSecrets) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        animationsDisabled = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
        // Deliberate pins, not oversights:
        //  - targetSdk 35 is specified by the product brief; OldTargetApi would nag forever.
        //  - dependency versions are frozen on purpose for a build-once app, so the three
        //    "there is a newer version" checks are noise rather than signal.
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
            "ObsoleteLintCustomCheck",
        )
        sarifReport = true
    }

    androidResources {
        generateLocaleConfig = false
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.leakcanary)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.accessibility)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * Hard guard for §1.1 of the spec: the release manifest must not request INTERNET.
 * Runs as part of `check`, so a regression fails CI rather than shipping.
 */
val verifyNoInternetPermission by tasks.registering {
    val manifest = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    inputs.file(manifest)
    outputs.upToDateWhen { false }
    doLast {
        val raw = manifest.asFile.readText()

        // Strip comments first. The manifest explains *why* there is no INTERNET permission,
        // and a naive substring scan would flag its own documentation.
        val text = raw.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

        val declared = Regex("""<uses-permission[^>]*android:name\s*=\s*"([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()

        val forbidden = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
        )
        val found = declared.intersect(forbidden).toList()
        if (found.isNotEmpty()) {
            throw GradleException("Forbidden permission(s) in AndroidManifest.xml: $found")
        }
        if (!text.contains("android:allowBackup=\"false\"")) {
            throw GradleException("AndroidManifest.xml must set android:allowBackup=\"false\"")
        }
    }
}

tasks.named("check") { dependsOn(verifyNoInternetPermission) }
