package dev.disagio.busroma.ricerca

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.ArrivoVicino
import dev.disagio.busroma.posizione.Posizione
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
}
