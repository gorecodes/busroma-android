package dev.disagio.busroma.storico

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Le ultime ricerche.
 *
 * SI MEMORIZZA CIO' CHE VIENE SCELTO, NON CIO' CHE VIENE DIGITATO. E' la
 * scelta piu' importante di questo file, ed e' la stessa del web: "term" e' un
 * testo da ridigitare, mentre una fermata scelta e' un posto dove tornare con
 * un tocco. Per questo ogni voce porta con se' quanto basta a ridisegnarsi
 * senza interrogare il server.
 */
@Serializable
sealed interface VoceStorico {
    val id: String
    val etichetta: String

    @Serializable
    data class Fermata(
        override val id: String,
        override val etichetta: String,
        val palina: String? = null,
    ) : VoceStorico

    @Serializable
    data class Linea(
        override val id: String,
        override val etichetta: String,
        val shortName: String,
        val tipo: Int = 3,
        val colore: String? = null,
        val coloreTesto: String? = null,
    ) : VoceStorico
}

private val Context.archivioStorico by preferencesDataStore(name = "storico")
private val CHIAVE = stringPreferencesKey("ricerche")
private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "tipo_voce" }

/** Cinque, come sul web: e' uno storico, non un archivio. */
private const val MAX = 5

object Storico {

    fun flusso(context: Context): Flow<List<VoceStorico>> =
        context.archivioStorico.data.map { p ->
            val grezzo = p[CHIAVE] ?: return@map emptyList()
            try {
                json.decodeFromString<List<VoceStorico>>(grezzo).take(MAX)
            } catch (e: Exception) {
                emptyList()
            }
        }

    /** In testa, senza duplicati, tenendo le ultime cinque. */
    suspend fun aggiungi(context: Context, voce: VoceStorico) {
        context.archivioStorico.edit { p ->
            val attuale = p[CHIAVE]?.let {
                try {
                    json.decodeFromString<List<VoceStorico>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            val senzaDoppioni = attuale.filterNot { it::class == voce::class && it.id == voce.id }
            p[CHIAVE] = json.encodeToString(listOf(voce) + senzaDoppioni.take(MAX - 1))
        }
    }

    suspend fun svuota(context: Context) {
        context.archivioStorico.edit { it.remove(CHIAVE) }
    }
}
