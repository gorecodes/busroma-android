package dev.disagio.busroma.ricerca

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.ArrivoVicino
import dev.disagio.busroma.posizione.Posizione
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StatoVicine {
    object Riposo : StatoVicine
    object InCorso : StatoVicine
    data class Trovati(
        val arrivi: List<ArrivoVicino>,
        val incertezzaM: Float? = null,
        val etaMin: Int = 0,
        /** Istante del calcolo: i minuti si ricavano da eta_ts, non dal server. */
        val adesso: Long = System.currentTimeMillis(),
    ) : StatoVicine
    data class Errore(val messaggio: String) : StatoVicine
}

/**
 * Lo stato degli arrivi vicini, in un ViewModel e non in un `remember`.
 *
 * PERCHE' UN VIEWMODEL. Con il `remember` lo stato veniva buttato ogni volta
 * che la schermata usciva dalla composizione: aprivi una fermata, tornavi
 * indietro, e il tasto "Usa la mia posizione" era di nuovo lì come se non
 * avessi mai concesso niente. Il ViewModel e' legato alla voce dello stack di
 * navigazione, quindi sopravvive all'andata e ritorno - la stessa ragione per
 * cui la ricerca conserva il testo digitato.
 *
 * E si CARICA DA SE' se il permesso c'e' gia': quel tasto esiste per chiedere
 * il permesso, e una volta concesso richiederlo e' attrito senza scopo. Chi
 * apre l'app in strada vuole vedere cosa passa, non premere un tasto per
 * autorizzare una cosa che ha gia' autorizzato.
 */
/** Trenta secondi: vedi il commento su [VicineViewModel.ciclo]. */
private const val INTERVALLO_MS = 30_000L

class VicineViewModel : ViewModel() {

    private val _stato = MutableStateFlow<StatoVicine>(StatoVicine.Riposo)
    val stato: StateFlow<StatoVicine> = _stato.asStateFlow()

    /**
     * Il caricamento automatico si fa UNA volta per vita del ViewModel: se
     * fallisce non si riprova a ogni ritorno sulla schermata, altrimenti al
     * chiuso - dove il GPS non aggancia - ogni passaggio dalla home
     * accenderebbe il localizzatore per dodici secondi.
     */
    private var giaProvato = false

    /**
     * L'ultima posizione buona, tenuta per il ciclo.
     *
     * Il ciclo ripassa sopra QUESTE coordinate invece di riaccendere il
     * localizzatore ogni trenta secondi: chi sta a una fermata non si sposta,
     * e una lettura GPS al minuto svuoterebbe la batteria per spostare un
     * numero di un minuto. La posizione si rilegge solo quando l'utente tocca
     * "Aggiorna", ed e' anche il motivo per cui l'eta della posizione si
     * ricalcola a ogni giro: se il dato invecchia, lo diciamo.
     */
    private var posizione: Location? = null

    fun caricaSePossibile(context: Context) {
        if (giaProvato) return
        if (!Posizione.permessoConcesso(context)) return
        carica(context)
    }

    fun carica(context: Context) {
        giaProvato = true
        _stato.value = StatoVicine.InCorso
        viewModelScope.launch {
            val pos = Posizione.corrente(context)
            if (pos == null) {
                // "Permesso negato" e "posizione non arrivata" sono due
                // problemi con due rimedi diversi: un messaggio unico
                // lascerebbe l'utente a indovinare.
                _stato.value = StatoVicine.Errore(
                    if (Posizione.permessoConcesso(context)) {
                        "Non riesco a leggere la posizione. Se sei al chiuso o in metro, capita."
                    } else {
                        "Serve il permesso di posizione."
                    },
                )
                return@launch
            }
            posizione = pos
            _stato.value = try {
                StatoVicine.Trovati(
                    arrivi = Api.arriviVicini(pos.latitude, pos.longitude).arrivals,
                    incertezzaM = if (pos.hasAccuracy()) pos.accuracy else null,
                    etaMin = ((System.currentTimeMillis() - pos.time) / 60_000L)
                        .coerceAtLeast(0L).toInt(),
                    adesso = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                StatoVicine.Errore("Gli arrivi qui intorno non arrivano.")
            }
        }
    }

    /**
     * Il ciclo di aggiornamento, che PRIMA NON C'ERA.
     *
     * Senza, i minuti di "Qui intorno" restavano congelati all'istante in cui
     * si era concesso il permesso, e l'unico modo di muoverli era il tasto
     * "Aggiorna": un difetto visto in uso, non in teoria.
     *
     * Trenta secondi e non quindici come alla fermata: qui gli arrivi sono di
     * otto paline diverse e la risposta e' piu' grossa, mentre chi guarda la
     * home sta scegliendo, non correndo. Sospende, e va lanciato dentro
     * `repeatOnLifecycle(RESUMED)`: in tasca non si interroga niente.
     */
    suspend fun ciclo(context: Context) {
        while (true) {
            delay(INTERVALLO_MS)
            val pos = posizione ?: continue
            try {
                val arrivi = Api.arriviVicini(pos.latitude, pos.longitude).arrivals
                _stato.value = StatoVicine.Trovati(
                    arrivi = arrivi,
                    incertezzaM = if (pos.hasAccuracy()) pos.accuracy else null,
                    etaMin = ((System.currentTimeMillis() - pos.time) / 60_000L)
                        .coerceAtLeast(0L).toInt(),
                    adesso = System.currentTimeMillis(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // SI TENGONO I DATI VECCHI, come alla fermata: un buco di rete
                // non deve svuotare la sezione mentre la stai leggendo.
            }
        }
    }
}
