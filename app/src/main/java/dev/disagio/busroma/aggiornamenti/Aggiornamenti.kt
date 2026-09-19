package dev.disagio.busroma.aggiornamenti

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.disagio.busroma.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

private val Context.archivio by preferencesDataStore(name = "aggiornamenti")

private val CHIAVE_ULTIMO_CONTROLLO = longPreferencesKey("ultimoControllo")
private val CHIAVE_VERSIONE_IGNORATA = intPreferencesKey("versionCodeIgnorata")
private val CHIAVE_DISPONIBILE = stringPreferencesKey("disponibile")
private val CHIAVE_ESITO_INSTALLAZIONE_ISTANTE = longPreferencesKey("esitoInstallazioneIstante")

/**
 * Quanto resta valido un esito di installazione non riuscita.
 *
 * Stesso ragionamento del filtro in [flussoDisponibile]: questo deposito
 * sopravvive a un'installazione riuscita, quindi un "installazione non
 * completata" letto un'ora dopo — magari dopo che l'utente ha già
 * aggiornato a mano — sarebbe una bugia peggiore del silenzio.
 */
private const val VALIDITA_ESITO_INSTALLAZIONE_MS = 5 * 60_000L

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

    // Client separato da Api: quello ha User-Agent, base URL e timeout
    // calibrati per le API di Bus Roma. Questo è un controllo di cortesia
    // su un file di duecento byte: timeout brevi e nessun overhead.
    /**
     * `ignoreUnknownKeys` perché il manifesto potrà crescere: un campo nuovo
     * aggiunto per una versione futura non deve rompere il controllo su un
     * telefono che non si è ancora aggiornato.
     */
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 5_000
            connectTimeoutMillis = 3_000
        }
    }

    /**
     * Controlla, rispettando il freno.
     *
     * @param forzato vero quando il controllo lo ha chiesto l'utente dal tasto
     *   nelle Informazioni: in quel caso il freno si ignora, perché un tasto
     *   che a volte non fa niente è peggio di nessun tasto.
     */
    suspend fun controlla(context: Context, forzato: Boolean = false): Esito {
        // La variante F-Droid non ha aggiornamenti in-app per definizione:
        // l'app la distribuisce il repository, non noi.
        if (!BuildConfig.AGGIORNAMENTI_IN_APP) return Esito.Nessuno

        val prefs = try {
            context.archivio.data.first()
        } catch (e: Exception) {
            // Archivio illeggibile: si va avanti come se non ci fossero dati.
            null
        }

        if (!forzato) {
            val ultimoControllo = prefs?.get(CHIAVE_ULTIMO_CONTROLLO) ?: 0L
            if (System.currentTimeMillis() - ultimoControllo < INTERVALLO_CONTROLLO_MS) {
                return Esito.Nessuno
            }
        }

        return try {
            // GITHUB SERVE GLI ALLEGATI COME application/octet-stream, non come
            // application/json — verificato con curl sulla release. Con la
            // negoziazione del contenuto la deserializzazione automatica
            // solleverebbe NoTransformationFoundException, il controllo
            // finirebbe in Esito.Errore a ogni giro, e avremmo un
            // aggiornatore che non aggiorna mai senza dire perché. Quindi si
            // legge il corpo come testo e si decodifica a mano.
            val remota = json.decodeFromString<VersioneRemota>(
                client.get(URL_ULTIMA).bodyAsText(),
            )
            // Si segna l'istante solo dopo aver ricevuto risposta: un errore
            // di rete non deve far scattare il freno dei 15 minuti.
            context.archivio.edit { p ->
                p[CHIAVE_ULTIMO_CONTROLLO] = System.currentTimeMillis()
            }
            val codiceIgnorato = prefs?.get(CHIAVE_VERSIONE_IGNORATA)
            val esito = if (daMostrare(remota, BuildConfig.VERSION_CODE, codiceIgnorato)) {
                Esito.Disponibile(remota)
            } else {
                Esito.Nessuno
            }
            // L'esito si SCRIVE, e chi disegna lo legge da [flussoDisponibile].
            context.archivio.edit { p ->
                if (esito is Esito.Disponibile) {
                    p[CHIAVE_DISPONIBILE] = json.encodeToString(remota)
                } else {
                    p.remove(CHIAVE_DISPONIBILE)
                }
            }
            esito
        } catch (e: CancellationException) {
            // La coroutine è stata annullata dall'esterno: non è un errore
            // di rete, si rilancia prima del catch generico. Stesso motivo
            // del commento in RicercaViewModel.
            throw e
        } catch (e: Exception) {
            Esito.Errore
        }
    }

    /**
     * "Non mostrarmi più questa versione": l'utente ha chiuso il banner.
     *
     * Si ricorda il versionCode e non un booleano, così la versione DOPO torna
     * ad annunciarsi da sé.
     */
    suspend fun ignora(context: Context, versionCode: Int) {
        context.archivio.edit { p ->
            p[CHIAVE_VERSIONE_IGNORATA] = versionCode
            // Via anche dall'esito salvato, altrimenti il banner chiuso
            // ricomparirebbe alla prossima composizione.
            p.remove(CHIAVE_DISPONIBILE)
        }
    }

    /**
     * La versione trovata dall'ultimo controllo riuscito, o null.
     *
     * STA SU DISCO E NON IN MEMORIA DI UNA SCHERMATA, e la ragione l'ha
     * insegnata una prova sul telefono: il controllo manuale nelle Informazioni
     * diceva "disponibile" mentre il banner in home — l'unico posto da cui si
     * scarica — restava zitto, perché erano due stati separati e il freno dei
     * quindici minuti impediva al secondo di scoprirlo. Scritto qui, l'esito è
     * uno solo per tutta l'app e non dipende da chi ha controllato per ultimo.
     */
    fun flussoDisponibile(context: Context): Flow<VersioneRemota?> =
        context.archivio.data.map { p ->
            val salvata = p[CHIAVE_DISPONIBILE]?.let {
                try {
                    json.decodeFromString<VersioneRemota>(it)
                } catch (e: Exception) {
                    null
                }
            }
            // SI FILTRA ANCHE ALLA LETTURA, e non basta filtrare quando si
            // scrive. Il deposito sopravvive all'aggiornamento dell'app: dopo
            // aver installato la versione annunciata, quella riga resta su
            // disco e il banner continuerebbe a offrire la versione che stai
            // gia' usando, fino al controllo successivo — cioe' fino a un
            // quarto d'ora di bugia. Difetto visto in uso.
            salvata?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        }

    /**
     * Registra che un'installazione non è arrivata in fondo: l'utente ha
     * rifiutato il dialogo di conferma del sistema, oppure è fallita per
     * un'altra ragione. Lo scrive [RicevitoreInstallazione], che è un
     * BroadcastReceiver e non ha modo di parlare a un composabile in
     * memoria — il DataStore è il tramite.
     */
    suspend fun segnaInstallazioneNonRiuscita(context: Context) {
        context.archivio.edit { p ->
            p[CHIAVE_ESITO_INSTALLAZIONE_ISTANTE] = System.currentTimeMillis()
        }
    }

    /**
     * "Ricomincio": l'utente ha toccato di nuovo Aggiorna. Un esito vecchio
     * non deve restare a raccontare un tentativo che non è più quello in
     * corso — se anche questo fallisce, ne scrive uno nuovo con l'istante
     * aggiornato.
     */
    suspend fun installazioneRicominciata(context: Context) {
        context.archivio.edit { p ->
            p.remove(CHIAVE_ESITO_INSTALLAZIONE_ISTANTE)
        }
    }

    /**
     * Vero se un'installazione è fallita di recente: il banner lo usa per
     * offrire "riprova" invece di restare impiccato sull'ultimo stato
     * mostrato prima che l'app venisse messa in secondo piano o uccisa.
     */
    fun flussoInstallazioneNonRiuscita(context: Context): Flow<Boolean> =
        context.archivio.data.map { p ->
            val istante = p[CHIAVE_ESITO_INSTALLAZIONE_ISTANTE] ?: return@map false
            System.currentTimeMillis() - istante < VALIDITA_ESITO_INSTALLAZIONE_MS
        }

    suspend fun ignorata(context: Context, versionCode: Int): Boolean {
        val prefs = try {
            context.archivio.data.first()
        } catch (e: Exception) {
            // Archivio illeggibile: si comporta come se non ci fosse nulla da ignorare.
            return false
        }
        return prefs[CHIAVE_VERSIONE_IGNORATA] == versionCode
    }
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
): Boolean = remota.versionCode > codiceInstallato && remota.versionCode != codiceIgnorato
