package dev.disagio.busroma.sveglie

import android.content.Context
import dev.disagio.busroma.dati.Arrivo

/**
 * La facciata: è tutto quello che l'interfaccia deve conoscere.
 *
 * Esiste per una ragione precisa: la campanella non deve sapere se dietro c'è
 * una sveglia locale, un servizio in primo piano o un giorno UnifiedPush.
 * Accendere e spegnere, e nient'altro.
 */
object Avvisami {

    /**
     * Accende la campanella su un arrivo: salva la vigilanza e programma il
     * primo controllo.
     *
     * Ritorna falso se non c'è niente da vigilare — [Arrivo.tripId] nullo
     * (arrivo da tabella, nessuna corsa da seguire) o [Arrivo.etaTs]
     * illeggibile.
     */
    suspend fun avvia(
        context: Context,
        stopId: String,
        nomeFermata: String?,
        arrivo: Arrivo,
    ): Boolean = TODO()

    suspend fun ferma(context: Context, tripId: String, stopId: String): Unit = TODO()

    /**
     * Il controllo di un risveglio, chiamato dal ricevitore.
     *
     * Legge la vigilanza, chiede l'ETA aggiornato con UNA richiesta
     * (`Api.corsa(tripId)`, e se la corsa non c'è — col feed ATAC fermo
     * `/api/trips/:id` torna vuoto — ripiego su `Api.arrivi(stopId)` cercando
     * lo stesso `tripId`), passa la palla a [decidi] ed esegue l'esito:
     * notifica, riprogramma, o chiude.
     */
    suspend fun controlla(context: Context, chiave: String): Unit = TODO()
}
