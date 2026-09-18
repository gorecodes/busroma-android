package dev.disagio.busroma.impostazioni

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * La scelta del tema, che deve durare.
 *
 * Tre valori e non due: "segui il sistema" e' lo stato iniziale, e non e' la
 * stessa cosa di "chiaro". Un utente che ha il telefono in automatico
 * giorno/notte non vuole essere inchiodato al chiaro solo perche' l'app e'
 * stata aperta di giorno.
 */
enum class SceltaTema { Sistema, Chiaro, Scuro }

private val Context.archivioTema by preferencesDataStore(name = "impostazioni")
private val CHIAVE = stringPreferencesKey("tema")

object Tema {

    fun flusso(context: Context): Flow<SceltaTema> =
        context.archivioTema.data.map { p ->
            when (p[CHIAVE]) {
                "chiaro" -> SceltaTema.Chiaro
                "scuro" -> SceltaTema.Scuro
                else -> SceltaTema.Sistema
            }
        }

    /**
     * La prima lettura, bloccante, fatta una volta all'avvio dell'attivita'.
     *
     * Bloccare il thread principale e' di solito un errore, e qui e' la scelta
     * giusta: sono pochi millisecondi da un file locale, e l'alternativa e'
     * disegnare il primo fotogramma col tema di sistema per poi cambiarlo -
     * cioe' il lampo di bianco che sul web e' stato eliminato con lo script
     * inline nel documento. Lo stesso problema, la stessa soluzione.
     */
    fun letturaIniziale(context: Context): SceltaTema = runBlocking {
        try {
            flusso(context).first()
        } catch (e: Exception) {
            SceltaTema.Sistema
        }
    }

    suspend fun imposta(context: Context, scelta: SceltaTema) {
        context.archivioTema.edit { p ->
            when (scelta) {
                SceltaTema.Sistema -> p.remove(CHIAVE)
                SceltaTema.Chiaro -> p[CHIAVE] = "chiaro"
                SceltaTema.Scuro -> p[CHIAVE] = "scuro"
            }
        }
    }
}
