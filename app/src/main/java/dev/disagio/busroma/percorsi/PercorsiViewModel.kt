package dev.disagio.busroma.percorsi

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.NessunItinerario
import dev.disagio.busroma.dati.Piano
import dev.disagio.busroma.posizione.Posizione
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Perché il calcolo è fallito.
 *
 * Due casi e non uno, perché richiedono due frasi diverse: [Vuoto] è una
 * risposta ("da qui non ci arrivi", niente da riprovare), [Guasto] è un
 * guasto (riprovare ha senso). Sul web la stessa distinzione si chiama
 * "vuoto" e "guasto".
 */
enum class ErrorePiano { Vuoto, Guasto }

enum class StatoGps { Fermo, Cerco, Negato, NonTrovata }

data class StatoPercorsi(
    val da: Capo? = null,
    val a: Capo? = null,
    /** Istante di partenza in millisecondi, o null per "adesso". */
    val quando: Long? = null,
    val piano: Piano? = null,
    val calcolando: Boolean = false,
    val errore: ErrorePiano? = null,
    /** Indice dell'itinerario aperto, null sull'elenco. */
    val aperto: Int? = null,
    val gps: StatoGps = StatoGps.Fermo,
) {
    val puoCercare: Boolean get() = da != null && a != null && !calcolando
}

/**
 * Lo stato del pianificatore.
 *
 * STA IN UN VIEWMODEL E NON IN `remember`, e la ragione è la stessa per cui
 * sul web sta nell'indirizzo invece che in memoria: entrare in una fermata
 * partendo da un itinerario e tornare indietro non deve cancellare il
 * percorso. Un ViewModel agganciato alla destinazione sopravvive finché quella
 * destinazione è nella pila, quindi il risultato è lo stesso senza dover
 * serializzare niente nella rotta.
 *
 * Non si ricalcola da soli al cambio dei capi: il calcolo costa e la ricerca
 * parte solo dal tasto, come sul web.
 */
class PercorsiViewModel : ViewModel() {

    private val _stato = MutableStateFlow(StatoPercorsi())
    val stato: StateFlow<StatoPercorsi> = _stato.asStateFlow()

    private var lavoro: Job? = null

    fun impostaDa(c: Capo?) { _stato.value = _stato.value.copy(da = c, gps = StatoGps.Fermo) }
    fun impostaA(c: Capo?) { _stato.value = _stato.value.copy(a = c) }
    fun impostaQuando(ms: Long?) { _stato.value = _stato.value.copy(quando = ms) }
    fun apri(indice: Int?) { _stato.value = _stato.value.copy(aperto = indice) }

    /**
     * La posizione attuale come capo di partenza.
     *
     * Si appoggia alla cascata in tre passi già usata da "Qui intorno" invece
     * di chiamare il LocationManager da qui: quella cascata esiste perché al
     * chiuso il solo GPS non restituisce nulla, e riscriverla male in un
     * secondo posto significherebbe riavere quel difetto solo qui.
     */
    fun usaLaMiaPosizione(context: Context) {
        if (!Posizione.permessoConcesso(context)) {
            _stato.value = _stato.value.copy(gps = StatoGps.Negato)
            return
        }
        _stato.value = _stato.value.copy(gps = StatoGps.Cerco)
        viewModelScope.launch {
            val p = Posizione.corrente(context)
            _stato.value = if (p == null) {
                _stato.value.copy(gps = StatoGps.NonTrovata)
            } else {
                _stato.value.copy(
                    da = Capo.MiaPosizione(p.latitude, p.longitude),
                    gps = StatoGps.Fermo,
                )
            }
        }
    }

    fun cerca() {
        val s = _stato.value
        val da = s.da ?: return
        val a = s.a ?: return

        lavoro?.cancel()
        // Il piano vecchio si BUTTA: mostrare i percorsi della richiesta
        // precedente mentre si calcola la nuova sarebbe una risposta sbagliata,
        // non una vecchia. È l'opposto di quel che si fa sugli arrivi, dove un
        // dato di trenta secondi prima vale ancora.
        _stato.value = s.copy(calcolando = true, errore = null, piano = null, aperto = null)

        lavoro = viewModelScope.launch {
            try {
                val piano = Api.pianifica(
                    daFermata = (da as? Capo.Fermata)?.stopId,
                    daLat = da.lat(),
                    daLon = da.lon(),
                    aFermata = (a as? Capo.Fermata)?.stopId,
                    aLat = a.lat(),
                    aLon = a.lon(),
                    quandoIso = s.quando?.let { iso(it) },
                )
                _stato.value = _stato.value.copy(
                    piano = piano,
                    calcolando = false,
                    // Un 200 con la lista vuota non dovrebbe capitare, ma se
                    // capita è il caso "vuoto", non un successo con niente da
                    // mostrare.
                    errore = if (piano.options.isEmpty()) ErrorePiano.Vuoto else null,
                )
            } catch (e: NessunItinerario) {
                _stato.value = _stato.value.copy(calcolando = false, errore = ErrorePiano.Vuoto)
            } catch (e: CancellationException) {
                // Il lavoro precedente e' stato annullato da cerca(): non e'
                // un guasto, e la nuova ricerca e' gia' partita.
                throw e
            } catch (e: Exception) {
                _stato.value = _stato.value.copy(calcolando = false, errore = ErrorePiano.Guasto)
            }
        }
    }
}

private fun Capo.lat(): Double? = when (this) {
    is Capo.MiaPosizione -> lat
    is Capo.Luogo -> lat
    is Capo.Fermata -> null
}

private fun Capo.lon(): Double? = when (this) {
    is Capo.MiaPosizione -> lon
    is Capo.Luogo -> lon
    is Capo.Fermata -> null
}

/**
 * L'istante scelto nel formato che vuole /api/plan.
 *
 * ISO con l'offset, non l'ora locale nuda: il server fa `Date.parse`, e una
 * stringa senza fuso verrebbe interpretata secondo il fuso del SERVER. Finché
 * gira in Europa la differenza non si vede, e si vedrebbe il giorno che si
 * sposta - cioè nel momento peggiore.
 */
private fun iso(ms: Long): String =
    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms))

/** L'istante scelto come lo legge un romano: "gio 18 set, 19:40". */
fun etichettaQuando(ms: Long): String {
    val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.of("Europe/Rome"))
    return DateTimeFormatter.ofPattern("EEE d MMM, HH:mm").format(t)
}
