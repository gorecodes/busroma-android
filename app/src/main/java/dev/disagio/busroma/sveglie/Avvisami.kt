package dev.disagio.busroma.sveglie

import android.content.Context
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.Arrivo
import java.time.Instant

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
    ): Boolean {
        val tripId = arrivo.tripId ?: return false
        val etaMs = try {
            Instant.parse(arrivo.etaTs).toEpochMilli()
        } catch (e: Exception) {
            return false
        }
        val adesso = System.currentTimeMillis()
        val v = Vigilanza(
            tripId = tripId,
            stopId = stopId,
            shortName = arrivo.shortName,
            headsign = arrivo.headsign,
            nomeFermata = nomeFermata,
            etaIso = arrivo.etaTs,
            // Come la pulizia a 2 ore del worker: una corsa cancellata non deve
            // tenere sveglio il telefono per sempre.
            scadenzaMs = minOf(etaMs + 30 * 60_000L, adesso + 2 * 3600_000L),
        )
        Vigilanze.salva(context, v)
        // Si esegue subito: se il bus è già a tre minuti, si notifica adesso
        // invece di programmare una sveglia senza senso.
        eseguiEsito(context, v, decidi(v, etaMs, adesso))
        return true
    }

    suspend fun ferma(context: Context, tripId: String, stopId: String) {
        val chiave = Vigilanza.chiaveDi(tripId, stopId)
        Vigilanze.rimuovi(context, chiave)
        Sveglie.annulla(context, chiave)
    }

    /**
     * Il controllo di un risveglio, chiamato dal ricevitore.
     *
     * Legge la vigilanza, chiede l'ETA aggiornato con UNA richiesta
     * (`Api.corsa(tripId)`, e se la corsa non c'è — col feed ATAC fermo
     * `/api/trips/:id` torna vuoto — ripiego su `Api.arrivi(stopId)` cercando
     * lo stesso `tripId`), passa la palla a [decidi] ed esegue l'esito:
     * notifica, riprogramma, o chiude.
     */
    suspend fun controlla(context: Context, chiave: String) {
        val v = Vigilanze.trova(context, chiave)
        if (v == null) {
            // La vigilanza è sparita (ferma() chiamato nel frattempo): pulizia.
            Sveglie.annulla(context, chiave)
            return
        }
        val adesso = System.currentTimeMillis()
        val etaMsFresco = leggiEta(v)
        eseguiEsito(context, v, decidi(v, etaMsFresco, adesso))
    }

    /**
     * Legge l'ETA fresco dal server con una sola richiesta, con ripiego.
     *
     * Prima la corsa: dà l'ETA per ogni fermata del percorso, anche quelle
     * che non compaiono nell'endpoint degli arrivi. Se la corsa non è
     * disponibile o non copre la fermata vigilata, si ricade sugli arrivi
     * della palina cercando la stessa corsa. Qualunque eccezione → null.
     */
    private suspend fun leggiEta(v: Vigilanza): Long? {
        val etaIso: String? = try {
            Api.corsa(v.tripId).stops.firstOrNull { it.stopId == v.stopId }?.etaTs
        } catch (e: Exception) {
            null
        } ?: try {
            Api.arrivi(v.stopId).arrivals.firstOrNull { it.tripId == v.tripId }?.etaTs
        } catch (e: Exception) {
            null
        }
        return etaIso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    }

    private suspend fun eseguiEsito(context: Context, v: Vigilanza, esito: Esito) {
        when (esito) {
            is Esito.Notifica -> {
                Notifiche.creaCanale(context)
                Notifiche.arrivo(context, v, esito.minuti, esito.fresco)
                Vigilanze.rimuovi(context, v.chiave)
                Sveglie.annulla(context, v.chiave)
            }
            is Esito.Ricontrolla -> {
                Vigilanze.salva(context, esito.vigilanza)
                Sveglie.programma(context, esito.vigilanza.chiave, esito.istanteMs)
            }
            Esito.Abbandona -> {
                Vigilanze.rimuovi(context, v.chiave)
                Sveglie.annulla(context, v.chiave)
            }
        }
    }
}
