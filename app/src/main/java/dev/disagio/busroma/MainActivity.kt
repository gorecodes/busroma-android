package dev.disagio.busroma

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.disagio.busroma.impostazioni.SceltaTema
import dev.disagio.busroma.impostazioni.Tema
import dev.disagio.busroma.sveglie.Notifiche
import dev.disagio.busroma.ui.theme.TemaBusRoma

class MainActivity : ComponentActivity() {

    // Stato osservabile da Compose: va aggiornato sia in onCreate (avvio freddo
    // dalla notifica) sia in onNewIntent (app già viva, launchMode singleTop).
    private var fermataDaAprire by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // PRIMA di super.onCreate: e' la libreria di compatibilita' che
        // sostituisce il tema dello splash con quello dell'app. Invertire le
        // due righe lascia lo splash appiccicato allo schermo.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Disegna sotto le barre di sistema: su un telefono lo spazio in
        // altezza e' la risorsa scarsa.
        enableEdgeToEdge()

        // Canale subito, prima di qualunque risveglio del ricevitore che
        // potrebbe notificare appena il processo si riavvia.
        Notifiche.creaCanale(this)

        fermataDaAprire = intent.getStringExtra(Notifiche.EXTRA_FERMATA)

        // La scelta del tema si legge PRIMA di comporre, bloccando per pochi
        // millisecondi su un file locale. L'alternativa e' disegnare il primo
        // fotogramma col tema di sistema e poi cambiarlo: e' il lampo di
        // bianco che sul web e' stato eliminato con lo script inline nel
        // documento. Stesso problema, stessa soluzione.
        val iniziale = Tema.letturaIniziale(this)

        setContent {
            // `remember` sul flusso, non `Tema.flusso(this)` nudo: quello
            // creerebbe un flusso NUOVO a ogni ricomposizione, e collectAsState
            // riavvierebbe la raccolta ogni volta - una lettura da disco per
            // ogni fotogramma ridisegnato.
            val flusso = remember { Tema.flusso(this) }
            val scelta by flusso.collectAsState(initial = iniziale)
            val scura = when (scelta) {
                SceltaTema.Chiaro -> false
                SceltaTema.Scuro -> true
                SceltaTema.Sistema -> isSystemInDarkTheme()
            }
            TemaBusRoma(scura = scura) {
                AppBusRoma(
                    fermataDaAprire = fermataDaAprire,
                    onFermataAperta = { fermataDaAprire = null },
                )
            }
        }
    }

    // Con launchMode="singleTop" il tocco sulla notifica non ricrea l'Activity
    // se è già in cima: arriva qui. Aggiornando lo stato osservabile la
    // navigazione parte senza bisogno di ricreare la composizione.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        fermataDaAprire = intent.getStringExtra(Notifiche.EXTRA_FERMATA)
    }
}
