package dev.disagio.busroma.preferiti

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Una fermata nei preferiti.
 *
 * SI SALVANO ANCHE NOME E PALINA, non solo l'identificativo. Sembra
 * duplicazione di dati che il server già conosce, ed è deliberato: con il solo
 * identificativo la schermata dei preferiti dovrebbe fare una chiamata per
 * ciascuno prima di poter mostrare qualcosa, e resterebbe vuota senza rete —
 * cioè proprio nel momento in cui uno apre l'app per sapere dove sta il suo
 * autobus. Con il nome salvato, la lista compare subito e sempre.
 *
 * Il prezzo è che un nome cambiato lato ATAC resta vecchio nei preferiti
 * finché l'utente non riapre quella fermata. È un prezzo che si paga volentieri.
 */
@Serializable
data class FermataPreferita(
    val stopId: String,
    val nome: String,
    val palina: String? = null,
)

/**
 * Il deposito dei preferiti, su DataStore.
 *
 * ILLIMITATI E ORDINATI, come sul web. L'ordine è la posizione nella lista, ed
 * è dato salvato e non calcolato: l'utente lo decide e deve restare. Il
 * riordino per trascinamento arriva subito dopo, ma il modello lo prevede già
 * da adesso, così aggiungerlo non significa migrare i dati di chi ha già
 * l'app.
 *
 * Un solo valore, una stringa JSON, invece di una chiave per fermata: serve a
 * conservare l'ordine, che un insieme di chiavi separate perderebbe.
 *
 * Non è una classe con dipendenze iniettate perché DataStore va istanziato una
 * volta sola per file, e la delega su Context lo garantisce. Con quattro
 * schermate, passare il Context è più leggibile di introdurre Hilt per questo.
 */
private val Context.archivio by preferencesDataStore(name = "preferiti")

private val CHIAVE = stringPreferencesKey("elenco")
private val json = Json { ignoreUnknownKeys = true }

object Preferiti {

    fun flusso(context: Context): Flow<List<FermataPreferita>> =
        context.archivio.data.map { p ->
            val grezzo = p[CHIAVE] ?: return@map emptyList()
            try {
                json.decodeFromString<List<FermataPreferita>>(grezzo)
            } catch (e: Exception) {
                // Un archivio illeggibile non deve impedire di usare l'app:
                // si riparte da una lista vuota invece di piantarsi.
                emptyList()
            }
        }

    /** Aggiunge in coda, o rimuove se c'è già: è il comportamento della stella. */
    suspend fun alterna(context: Context, f: FermataPreferita) {
        modifica(context) { elenco ->
            if (elenco.any { it.stopId == f.stopId }) {
                elenco.filterNot { it.stopId == f.stopId }
            } else {
                elenco + f
            }
        }
    }

    suspend fun rimuovi(context: Context, stopId: String) {
        modifica(context) { elenco -> elenco.filterNot { it.stopId == stopId } }
    }

    /** Sposta un elemento, per il riordino a trascinamento. */
    suspend fun sposta(context: Context, da: Int, a: Int) {
        modifica(context) { elenco ->
            if (da !in elenco.indices || a !in elenco.indices) return@modifica elenco
            elenco.toMutableList().apply { add(a, removeAt(da)) }
        }
    }

    private suspend fun modifica(
        context: Context,
        trasforma: (List<FermataPreferita>) -> List<FermataPreferita>,
    ) {
        context.archivio.edit { p ->
            val attuale = p[CHIAVE]?.let {
                try {
                    json.decodeFromString<List<FermataPreferita>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            p[CHIAVE] = json.encodeToString(trasforma(attuale))
        }
    }
}
