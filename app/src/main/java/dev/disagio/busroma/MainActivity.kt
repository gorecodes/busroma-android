package dev.disagio.busroma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.disagio.busroma.impostazioni.SceltaTema
import dev.disagio.busroma.impostazioni.Tema
import dev.disagio.busroma.ui.theme.TemaBusRoma

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disegna sotto le barre di sistema: su un telefono lo spazio in
        // altezza e' la risorsa scarsa.
        enableEdgeToEdge()

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
                AppBusRoma()
            }
        }
    }
}
