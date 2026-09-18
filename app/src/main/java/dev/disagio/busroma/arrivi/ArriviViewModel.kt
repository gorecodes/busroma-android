package dev.disagio.busroma.arrivi

import androidx.lifecycle.ViewModel
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.Arrivo
import dev.disagio.busroma.dati.Fermata
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ogni 15 secondi, come sul web: è il ritmo del feed ATAC. */
private const val INTERVALLO_MS = 15_000L

data class StatoArrivi(
    val fermata: Fermata? = null,
    val arrivi: List<Arrivo> = emptyList(),
    /** Vero solo al primissimo caricamento, quando non c'è ancora nulla da mostrare. */
    val primoCaricamento: Boolean = true,
    /** L'ULTIMO tentativo è fallito. Non significa che non ci sia nulla da mostrare. */
    val errore: Boolean = false,
    /**
     * L'istante a cui sono stati calcolati i minuti mostrati.
     *
     * Sta nello stato e non si legge dall'orologio al disegno, perché così
     * l'attesa si aggiorna a ogni battito anche quando una chiamata fallisce:
     * altrimenti, con la rete giù, i minuti resterebbero congelati sull'ultimo
     * valore riuscito e sembrerebbero veri.
     */
    val adesso: Long = System.currentTimeMillis(),
)

class ArriviViewModel(private val stopId: String) : ViewModel() {

    private val _stato = MutableStateFlow(StatoArrivi())
    val stato: StateFlow<StatoArrivi> = _stato.asStateFlow()

    /**
     * Il ciclo di aggiornamento. Sospende, e va lanciato dalla schermata
     * dentro `repeatOnLifecycle(RESUMED)`: quando l'app va in background la
     * coroutine viene annullata e le richieste si fermano. Lasciarlo girare
     * nello scope del ViewModel significherebbe interrogare le API dalla
     * tasca, che è la stessa ragione per cui sul web `usePolling` si sospende
     * a scheda nascosta.
     */
    suspend fun ciclo() {
        while (true) {
            aggiorna()
            delay(INTERVALLO_MS)
        }
    }

    private suspend fun aggiorna() {
        try {
            val r = Api.arrivi(stopId)
            _stato.value = StatoArrivi(
                fermata = r.stop,
                arrivi = r.arrivals,
                primoCaricamento = false,
                errore = false,
                adesso = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            // SI TENGONO I DATI VECCHI. Un errore di rete non deve cancellare
            // l'informazione che l'utente sta leggendo: a una fermata, "il 117
            // fra 4 minuti, ma il dato è di un minuto fa" è utile, una
            // schermata vuota no. Lo stesso principio del web, dove la lista
            // resta e compare solo un avviso.
            _stato.value = _stato.value.copy(
                primoCaricamento = false,
                errore = true,
                adesso = System.currentTimeMillis(),
            )
        }
    }

    /** Ricarica subito, per il tocco su "Riprova". */
    suspend fun riprova() = aggiorna()
}
