package dev.disagio.busroma.ricerca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.FermataTrovata
import dev.disagio.busroma.dati.Linea
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 250 millisecondi, la stessa attesa del web.
 *
 * Non e' un numero arbitrario: sotto, si interroga il server a ogni tasto e
 * "termini" diventa sette chiamate; sopra, la digitazione sembra inceppata.
 */
private const val ATTESA_MS = 250L

data class StatoRicerca(
    val testo: String = "",
    val linee: List<Linea> = emptyList(),
    val fermate: List<FermataTrovata> = emptyList(),
    val cercando: Boolean = false,
    val errore: Boolean = false,
) {
    val vuoto: Boolean get() = linee.isEmpty() && fermate.isEmpty()
}

class RicercaViewModel : ViewModel() {

    private val _stato = MutableStateFlow(StatoRicerca())
    val stato: StateFlow<StatoRicerca> = _stato.asStateFlow()

    /**
     * L'attesa e' implementata con un lavoro annullabile invece che con
     * l'operatore `debounce` sui flussi: fa la stessa cosa del `setTimeout`
     * piu' `clearTimeout` del web, si legge senza conoscere gli operatori, e
     * non dipende da API sperimentali.
     *
     * L'annullamento risolve anche le risposte fuori ordine: senza, una
     * richiesta lenta partita per "term" potrebbe arrivare DOPO quella per
     * "termini" e sovrascrivere i risultati buoni con i vecchi. Sul web la
     * stessa cosa e' gestita con la variabile `ignore`.
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
                // LINEE E FERMATE IN PARALLELO, come sul web: sono due
                // endpoint indipendenti, e farle in sequenza raddoppierebbe
                // l'attesa per niente.
                // coroutineScope garantisce che se una delle due fallisce,
                // l'altra viene annullata prima che l'eccezione raggiunga il catch.
                coroutineScope {
                    val attesaLinee = async { Api.cercaLinee(pulito).routes }
                    val attesaFermate = async { Api.cercaFermate(pulito).stops }

                    _stato.value = _stato.value.copy(
                        linee = attesaLinee.await(),
                        fermate = attesaFermate.await(),
                        cercando = false,
                        errore = false,
                    )
                }
            } catch (e: CancellationException) {
                // Il lavoro precedente e' stato annullato da scrivi(): non e'
                // un errore di rete, e la nuova ricerca e' gia' partita.
                throw e
            } catch (e: Exception) {
                // Qui i risultati vecchi SI BUTTANO, al contrario degli
                // arrivi: mostrare i risultati di "termini" mentre l'utente ha
                // scritto "tuscolana" sarebbe una risposta sbagliata, non una
                // vecchia.
                _stato.value = _stato.value.copy(
                    linee = emptyList(),
                    fermate = emptyList(),
                    cercando = false,
                    errore = true,
                )
            }
        }
    }
}
