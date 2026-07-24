import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Signier-Zugangsdaten aus einer gitignorierten Datei laden (falls vorhanden).
// Ohne diese Datei baut der Release-Typ unsigniert weiter – z. B. für Forks.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasKeystore) FileInputStream(keystorePropertiesFile).use { load(it) }
}

// --- Version aus Git ableiten (Single Source of Truth) ---
// versionName kommt aus dem letzten Tag: "1.0.1" bei exaktem Tag, sonst
// "1.0.1-3-gabc123(-dirty)" für Dev-Builds. versionCode bleibt manuell (unten).
// `providers.exec` ist die Configuration-Cache-taugliche Art, git aufzurufen.
fun gitValue(vararg args: String): String = runCatching {
    providers.exec {
        commandLine(listOf("git") + args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")

val versionNameFromGit: String =
    gitValue("describe", "--tags", "--always", "--dirty").ifBlank { "v0.0.0" }.removePrefix("v")
val gitSha: String = gitValue("rev-parse", "--short", "HEAD").ifBlank { "unknown" }

android {
    namespace = "de.kewl.boatspeedy"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.kewl.boatspeedy"
        minSdk = 33
        targetSdk = 35
        versionCode = 20                       // manuell, altes kleines Schema (steigt je Release)
        versionName = versionNameFromGit        // aus Git-Tag (Option A)
        resValue("string", "app_name", "BoatSpeedy")
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // DEV als eigenes Paket (…​.debug) + eigenes Label „BoatSpeedy DEV" → liegt neben
        // der echten App. Signiert mit dem Release-Keystore, damit Dev-über-Dev-Updates
        // ohne Deinstallieren funktionieren.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "BoatSpeedy DEV")
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.location)
    implementation(libs.osmdroid.android)
}
