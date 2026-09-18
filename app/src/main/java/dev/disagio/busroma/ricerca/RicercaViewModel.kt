package dev.disagio.busroma.ricerca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.FermataTrovata
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 250 millisecondi, la stessa attesa del web.
 *
 * Non è un numero arbitrario: sotto, si interroga il server a ogni tasto e la
 * ricerca "termini" diventa sette chiamate; sopra, la digitazione sembra
 * inceppata.
 */
private const val ATTESA_MS = 250L

data class StatoRicerca(
    val testo: String = "",
    val risultati: List<FermataTrovata> = emptyList(),
    val cercando: Boolean = false,
    val errore: Boolean = false,
)

class RicercaViewModel : ViewModel() {

    private val _stato = MutableStateFlow(StatoRicerca())
    val stato: StateFlow<StatoRicerca> = _stato.asStateFlow()

    /**
     * L'attesa è implementata con un lavoro annullabile invece che con
     * l'operatore `debounce` sui flussi: fa esattamente la stessa cosa del
     * `setTimeout` più `clearTimeout` del web, si legge senza conoscere gli
     * operatori, e non dipende da API ancora marcate come sperimentali.
     *
     * L'annullamento risolve anche il problema delle risposte fuori ordine:
     * senza, una richiesta lenta partita per "term" potrebbe arrivare DOPO
     * quella per "termini" e sovrascrivere i risultati buoni con quelli
     * vecchi. Sul web la stessa cosa è gestita con la variabile `ignore`.
     */
    private var lavoro: Job? = null

    fun scrivi(testo: String) {
        lavoro?.cancel()
        val pulito = testo.trim()

        if (pulito.isEmpty()) {
            _stato.value = StatoRicerca(testo = testo)
            return
        }

        _stato.value = _stato.value.copy(testo = testo, cercando = true, errore = false)

        lavoro = viewModelScope.launch {
            delay(ATTESA_MS)
            try {
                val r = Api.cercaFermate(pulito)
                _stato.value = _stato.value.copy(
                    risultati = r.stops,
                    cercando = false,
                    errore = false,
                )
            } catch (e: Exception) {
                // Qui i risultati vecchi SI BUTTANO, al contrario degli arrivi:
                // mostrare i risultati di "termini" mentre l'utente ha scritto
                // "tuscolana" sarebbe una risposta sbagliata, non una vecchia.
                _stato.value = _stato.value.copy(
                    risultati = emptyList(),
                    cercando = false,
                    errore = true,
                )
            }
        }
    }
}
