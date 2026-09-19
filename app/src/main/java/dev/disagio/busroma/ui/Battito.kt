package dev.disagio.busroma.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * L'orologio che batte, per le attese che devono scendere da sole.
 *
 * QUESTA È LA CORREZIONE DI UN DIFETTO VISTO IN USO. I minuti mostrati si
 * calcolano da `eta_ts` meno "adesso", e finora "adesso" veniva scritto solo
 * quando arrivava una risposta dal server: quindi l'attesa scendeva a scatti
 * di quindici o trenta secondi, e dove non c'era nessun ciclo di
 * aggiornamento — "Qui intorno" — restava ferma per sempre. Guardando lo
 * schermo per dieci secondi sembrava, correttamente, che l'app non si
 * aggiornasse.
 *
 * Sul web questo battito c'è e si chiama `useNow`. Nel porting era stato
 * perso, e con lui la ragione per cui l'attesa sembra viva.
 *
 * SI FERMA IN BACKGROUND: il battito gira dentro `repeatOnLifecycle(RESUMED)`,
 * quindi a schermata non in primo piano non c'è nessuna sveglia al secondo che
 * consuma batteria per aggiornare numeri che nessuno sta guardando.
 *
 * @param periodoMs ogni quanto ribattere. Un secondo per difetto: l'attesa è
 *   in minuti, quindi basterebbe meno spesso, ma un secondo garantisce che il
 *   salto da "2" a "1" avvenga nel momento giusto invece che fino a mezzo
 *   minuto dopo.
 */
@Composable
fun battito(periodoMs: Long = 1_000L): Long {
    var adesso by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(periodoMs) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(periodoMs)
                adesso = System.currentTimeMillis()
            }
        }
    }
    return adesso
}
