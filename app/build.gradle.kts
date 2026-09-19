// `java` dentro un build script Kotlin e' l'estensione del plugin Java,
// non il package: senza questo import, java.util.Properties non si risolve.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Le credenziali di firma stanno FUORI dal repository, in
 * ~/.busroma/firma.properties con permessi 600.
 *
 * Non in gradle.properties e non in un file del progetto: una chiave di
 * firma committata per sbaglio non si revoca: chiunque l'abbia puo'
 * pubblicare aggiornamenti che i telefoni accettano come nostri.
 *
 * Se il file non c'e', la build di rilascio esce NON FIRMATA invece di
 * fallire: chi clona il progetto deve poterlo compilare senza avere la
 * chiave di nessun altro.
 */
val credenzialiFirma = File(System.getProperty("user.home"), ".busroma/firma.properties")
    .takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    // Da AGP 8 il namespace sta qui e NON nel manifest.
    namespace = "dev.disagio.busroma"
    // Le piattaforme ora hanno una minor (android-37.2, non android-37) e le
    // librerie AndroidX pretendono di essere compilate contro la 37: con la 36
    // la build si ferma elencando una per una le dipendenze incompatibili.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "dev.disagio.busroma"
        // 26 = Android 8.0. A Roma girano ancora molti telefoni vecchi, e
        // Compose parte dalla 21: non c'e' motivo di alzare l'asticella.
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "0.1.8"
    }

    signingConfigs {
        if (credenzialiFirma != null) {
            create("rilascio") {
                storeFile = File(credenzialiFirma.getProperty("storeFile"))
                storePassword = credenzialiFirma.getProperty("storePassword")
                keyAlias = credenzialiFirma.getProperty("keyAlias")
                keyPassword = credenzialiFirma.getProperty("keyPassword")
                // Entrambi gli schemi: v2 basta da Android 7, v1 serve ai
                // telefoni piu' vecchi che il minSdk 26 non esclude del tutto
                // nel caso di installazioni laterali.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        all {
            buildConfigField("String", "BASE_URL", "\"https://bus.disagio.dev\"")
        // Chiave MapTiler, vuota per difetto: senza, la mappa ricade sulle
        // tessere raster di OpenStreetMap come fa il web. Si passa dalla riga
        // di comando o da gradle.properties senza finire nel repository:
        //   ./gradlew assembleRelease -PmaptilerKey=xxxx
        buildConfigField(
            "String",
            "MAPTILER_KEY",
            "\"${project.findProperty("maptilerKey") ?: ""}\"",
        )
        // Il repository da cui l'app legge le proprie release.
        buildConfigField("String", "REPO_RILASCI", "\"gorecodes/busroma-android\"")
        // L'AGGIORNATORE INTERNO SI PUO' SPEGNERE:
        //   ./gradlew assembleRelease -PaggiornamentiInApp=false
        // Serve alla variante per F-Droid, che non deve aggiornarsi da se'
        // perche' l'aggiornamento lo fa lo store: un'app che scarica APK da
        // sola, dentro F-Droid, non e' ammessa.
        buildConfigField(
            "Boolean",
            "AGGIORNAMENTI_IN_APP",
            "${project.findProperty("aggiornamentiInApp") ?: true}",
        )
        }
        release {
            // R8 SPENTO, di proposito. Ktor e kotlinx.serialization si
            // appoggiano alla reflection e ai serializzatori generati, e
            // senza le regole giuste l'offuscamento li rompe a RUNTIME: la
            // build riesce e l'app crolla aprendo una fermata. Per una
            // distribuzione fra amici non vale il rischio; quando servira'
            // ridurre il peso, si accende con le regole e si riprova ogni
            // schermata.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("rilascio")

            // SOLO LE ARCHITETTURE DEI TELEFONI VERI. MapLibre porta librerie
            // native per quattro architetture: su 54 MB di APK, 42 erano
            // librerie e 22 di quelle erano x86 e x86_64, che esistono solo
            // negli emulatori. Toglierle dalla build di rilascio dimezza il
            // file che si manda agli amici e non toglie niente a nessuno.
            //
            // Restano in quella di DEBUG, dove servono per far girare l'app
            // su un emulatore durante lo sviluppo.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        // Serve per il campo BASE_URL nei buildTypes.
        buildConfig = true
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // Lo splash dichiarato dal tema: su Android 12+ lo gestisce il
    // sistema, questa libreria lo porta indietro fino al minSdk.
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // MAPLIBRE NATIVE, la controparte esatta di maplibre-gl del web: consuma
    // lo stesso stile JSON, quindi le due mappe restano una cosa sola invece
    // di due implementazioni da tenere allineate a mano. L'alternativa erano
    // le mappe di Google: chiave, fatturazione, e un aspetto che non c'entra
    // niente col resto.
    implementation(libs.maplibre.android)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
