package dev.disagio.busroma.aggiornamenti

import android.content.Context
import kotlinx.serialization.Serializable

/**
 * La versione pubblicata, come la descrive il file allegato alla release.
 *
 * NON SI USA L'API DI GITHUB. Quella ha un limite di 60 richieste all'ora per
 * indirizzo IP senza autenticazione, e dietro il NAT di un operatore mobile
 * quel limite lo esaurisce qualcun altro per te. Si legge invece
 * `releases/latest/download/ultima-versione.json`, che è un redirect servito
 * dalla CDN: nessun limite, nessuna chiave, e l'indirizzo non cambia mai
 * perché è il nome dell'allegato a essere fisso, non la versione.
 */
@Serializable
data class VersioneRemota(
    val versionCode: Int,
    val versionName: String,
    /** Il nome dell'APK allegato alla release: serve a costruirne l'indirizzo. */
    val apk: String,
    /** Il changelog, se la release ne ha uno: è il testo di fastlane. */
    val note: String? = null,
)

/** L'esito di un controllo. */
sealed interface Esito {
    /** Siamo aggiornati, oppure è troppo presto per richiedere. */
    object Nessuno : Esito
    data class Disponibile(val versione: VersioneRemota) : Esito
    /** Rete assente o risposta illeggibile: non si dice niente all'utente. */
    object Errore : Esito
}

/**
 * QUINDICI MINUTI, non un giorno.
 *
 * Il ritmo lo decide chi pubblica, non chi usa: qui capita di fare più build
 * nello stesso pomeriggio, e un controllo giornaliero mostrerebbe la versione
 * di ieri. Il costo di un controllo è duecento byte, quindi il freno serve solo
 * a non ripetere la richiesta a ogni ritorno in primo piano.
 */
const val INTERVALLO_CONTROLLO_MS = 15 * 60_000L

/**
 * Il controllo degli aggiornamenti.
 *
 * Non c'è nessun lavoro in background e non c'è WorkManager: si guarda quando
 * l'app passa in primo piano, che è anche l'unico momento in cui un avviso
 * serve a qualcosa. Un aggiornamento annunciato mentre il telefono è in tasca
 * è una notifica che nessuno ha chiesto.
 */
object Aggiornamenti {

    /** Sempre l'ultima release, qualunque sia: il nome dell'allegato è fisso. */
    val URL_ULTIMA: String
        get() = "https://github.com/${dev.disagio.busroma.BuildConfig.REPO_RILASCI}" +
            "/releases/latest/download/ultima-versione.json"

    fun urlApk(nomeApk: String): String =
        "https://github.com/${dev.disagio.busroma.BuildConfig.REPO_RILASCI}" +
            "/releases/latest/download/$nomeApk"

    /**
     * Controlla, rispettando il freno.
     *
     * @param forzato vero quando il controllo lo ha chiesto l'utente dal tasto
     *   nelle Informazioni: in quel caso il freno si ignora, perché un tasto
     *   che a volte non fa niente è peggio di nessun tasto.
     */
    suspend fun controlla(context: Context, forzato: Boolean = false): Esito = TODO()

    /**
     * "Non mostrarmi più questa versione": l'utente ha chiuso il banner.
     *
     * Si ricorda il versionCode e non un booleano, così la versione DOPO torna
     * ad annunciarsi da sé.
     */
    suspend fun ignora(context: Context, versionCode: Int): Unit = TODO()

    suspend fun ignorata(context: Context, versionCode: Int): Boolean = TODO()
}

/**
 * Se la versione remota vada mostrata o no.
 *
 * Funzione pura e separata dal resto perché è l'unico pezzo con dentro una
 * decisione: il confronto è sul `versionCode` e non sul nome, perché il nome è
 * per gli umani e "0.10.0" verrebbe prima di "0.9.0" in qualunque ordinamento
 * di stringhe.
 */
fun daMostrare(
    remota: VersioneRemota,
    codiceInstallato: Int,
    codiceIgnorato: Int?,
): Boolean = TODO()
