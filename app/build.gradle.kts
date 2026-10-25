plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.wtzg.domino"
    compileSdk = 35

    // Version comes from the release (./gradlew assembleRelease -PappVersion=2.1.0).
    // versionCode is derived so every release installs over the one before.
    val appVersion = (project.findProperty("appVersion") as String?) ?: "2.1.0"
    val (major, minor, patch) = appVersion.split(".").map { it.toInt() }.also {
        require(it.size == 3) { "appVersion must look like 2.1.0" }
    }

    defaultConfig {
        applicationId = "de.wtzg.domino"
        minSdk = 26
        targetSdk = 35
        versionCode = major * 10_000 + minor * 100 + patch
        versionName = appVersion
        // Online server used unless players enter another one (set in gradle.properties).
        val serverUrl = (project.findProperty("dominoServerUrl") as String?).orEmpty()
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }

    // Releases are signed with the permanent release key when it's provided (CI secrets
    // or local env vars); otherwise with the debug key, which is fine for testing but
    // can't update an installed release. See docs/RELEASING.md.
    val releaseKeystore = System.getenv("DOMINO_KEYSTORE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("DOMINO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DOMINO_KEY_ALIAS") ?: "dominoes"
                keyPassword = System.getenv("DOMINO_KEY_PASSWORD") ?: System.getenv("DOMINO_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
        // Newer library versions exist; upgrades are done deliberately, not by lint.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":protocol"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.process)
    // QR codes: drawing invite codes, and scanning them with the Play services scanner.
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    // Runs the real game server inside online end-to-end tests.
    testImplementation(project(":server"))
    testImplementation(libs.ktor.server.netty)
}
