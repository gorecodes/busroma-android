// I plugin si dichiarano qui senza applicarli: le versioni vivono nel
// catalogo in gradle/libs.versions.toml, i moduli le ereditano.
//
// NOTA: da AGP 9.0 il supporto Kotlin e' integrato nel plugin Android e il
// plugin 'org.jetbrains.kotlin.android' non va piu' dichiarato - dichiararlo
// fa fallire la build con un errore esplicito.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
