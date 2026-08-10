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

// --- Version: fest verdrahtet (Single Source of Truth) ---
// versionName/versionCode werden manuell gepflegt (F-Droid liest versionName statisch aus
// dieser Datei, und ein fester Wert ist zudem reproduzierbar). Dev-Builds bekommen den
// Suffix "-dev" (siehe buildTypes) und die Git-SHA über BuildConfig.GIT_SHA.
// `providers.exec` ist die Configuration-Cache-taugliche Art, git aufzurufen.
fun gitValue(vararg args: String): String = runCatching {
    providers.exec {
        commandLine(listOf("git") + args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")

val gitSha: String = gitValue("rev-parse", "--short", "HEAD").ifBlank { "unknown" }

android {
    namespace = "de.kewl.boatspeedy"
    compileSdk = 35

    // Kein „Dependency metadata"-Signaturblock in der APK – F-Droid lehnt den ab
    // (und er ist ohnehin überflüssig).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "de.kewl.boatspeedy"
        minSdk = 33
        targetSdk = 35
        versionCode = 35                       // manuell, altes kleines Schema (steigt je Release)
        versionName = "1.3.0"                   // manuell (F-Droid-lesbar + reproduzierbar)
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
            // Keine Git-/VCS-Infos in die APK schreiben – das ist die einzige nicht
            // reproduzierbare Datei (version-control-info.textproto). Aus = reproducible builds.
            vcsInfo {
                include = false
            }
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
    implementation(libs.osmdroid.android)
}
