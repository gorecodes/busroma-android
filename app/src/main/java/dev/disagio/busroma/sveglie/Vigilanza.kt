package dev.disagio.busroma.sveglie

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Una vigilanza: "avvisami quando la corsa [tripId] arriva alla fermata
 * [stopId]".
 *
 * È la stessa identità di una riga di `push_subscriptions` sul web — corsa più
 * fermata — perché è la stessa domanda. Quel che cambia è chi tiene d'occhio
 * l'orologio: là il worker, qui il telefono.
 *
 * SI PORTA DIETRO IL NOME DELLA LINEA E IL CAPOLINEA, non solo gli
 * identificativi: la notifica deve poter dire "117 in arrivo — Termini" anche
 * quando scatta a processo riavviato e senza rete, esattamente come i preferiti
 * si salvano il nome della fermata per non dipendere da una chiamata.
 */
@Serializable
data class Vigilanza(
    val tripId: String,
    val stopId: String,
    val shortName: String,
    val headsign: String? = null,
    val nomeFermata: String? = null,
    /** L'ETA noto all'ultimo controllo riuscito, ISO-8601 come dal server. */
    val etaIso: String,
    /**
     * Oltre questo istante la vigilanza si abbandona (millisecondi epoch).
     * Serve a non tenere sveglio il telefono per una corsa cancellata.
     */
    val scadenzaMs: Long,
    /** Controlli consecutivi andati male: rete assente o corsa introvabile. */
    val tentativiFalliti: Int = 0,
) {
    /** L'identificativo stabile: è la chiave del deposito e della sveglia. */
    val chiave: String get() = chiaveDi(tripId, stopId)

    companion object {
        fun chiaveDi(tripId: String, stopId: String): String = "$tripId::$stopId"
    }
}

// DataStore istanziato una volta sola per processo, come in Preferiti.
private val Context.archivio by preferencesDataStore(name = "sveglie")
private val CHIAVE = stringPreferencesKey("elenco")
private val json = Json { ignoreUnknownKeys = true }

private fun decodifica(grezzo: String): List<Vigilanza> =
    try {
        json.decodeFromString(grezzo)
    } catch (e: Exception) {
        // Un archivio illeggibile non deve impedire di usare l'app.
        emptyList()
    }

/**
 * Il deposito delle vigilanze attive, su DataStore.
 *
 * Stesso schema di [dev.disagio.busroma.preferiti.Preferiti]: un solo valore,
 * una stringa JSON, e la delega su Context perché DataStore va istanziato una
 * volta per file. Le vigilanze devono sopravvivere alla morte del processo e al
 * riavvio del telefono: al risveglio il ricevitore trova qui tutto quello che
 * gli serve.
 */
object Vigilanze {

    /** Per l'interfaccia: quali campanelle sono accese. */
    fun flusso(context: Context): Flow<List<Vigilanza>> =
        context.archivio.data.map { p ->
            val grezzo = p[CHIAVE] ?: return@map emptyList()
            decodifica(grezzo)
        }

    /** Per i ricevitori: una lettura sola, senza osservare. */
    suspend fun elenco(context: Context): List<Vigilanza> =
        flusso(context).first()

    suspend fun trova(context: Context, chiave: String): Vigilanza? =
        elenco(context).find { it.chiave == chiave }

    /** Inserisce o sostituisce, per [Vigilanza.chiave]. */
    suspend fun salva(context: Context, v: Vigilanza) {
        context.archivio.edit { p ->
            val attuale = p[CHIAVE]?.let { decodifica(it) } ?: emptyList()
            p[CHIAVE] = json.encodeToString(attuale.filterNot { it.chiave == v.chiave } + v)
        }
    }

    suspend fun rimuovi(context: Context, chiave: String) {
        context.archivio.edit { p ->
            val attuale = p[CHIAVE]?.let { decodifica(it) } ?: emptyList()
            p[CHIAVE] = json.encodeToString(attuale.filterNot { it.chiave == chiave })
        }
    }
}
