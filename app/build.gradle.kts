plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

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
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        all {
            buildConfigField("String", "BASE_URL", "\"https://bus.disagio.dev\"")
        }
        release {
            isMinifyEnabled = false
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
}
