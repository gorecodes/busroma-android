package dev.disagio.busroma.aggiornamenti

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lo scaricamento e l'installazione dell'APK.
 *
 * UN'APP NON PRIVILEGIATA NON PUÒ INSTALLARSI DA SOLA, e non è una limitazione
 * da aggirare: l'ultima parola è dell'utente, sempre. Si può automatizzare il
 * controllo e lo scaricamento, poi compare il dialogo di sistema e lo conferma
 * lui. Silenzioso si fa solo da app di sistema o device owner.
 *
 * LA FIRMA LA VERIFICA ANDROID, non questo codice: un APK firmato con una
 * chiave diversa da quella dell'app installata viene RIFIUTATO
 * dall'installatore. Quindi anche un download manomesso non si installa, e non
 * serve controllare digest a mano — servirebbe solo a sbagliarlo.
 */
sealed interface StatoInstallazione {
    object Riposo : StatoInstallazione
    /** Percentuale 0-100: l'APK pesa una trentina di megabyte, va detto. */
    data class Scarico(val percento: Int) : StatoInstallazione
    /** Manca l'autorizzazione "installa app sconosciute" per questa app. */
    object PermessoMancante : StatoInstallazione
    /** Consegnato al dialogo di sistema: da qui decide l'utente. */
    object InAttesaDiConferma : StatoInstallazione
    data class Errore(val messaggio: String) : StatoInstallazione
}

object Installatore {

    /** `canRequestPackageInstalls`: l'autorizzazione si concede una volta sola. */
    fun permessoConcesso(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * L'intento che porta alla schermata di sistema dove si concede.
     *
     * Va aperto solo dopo che l'utente ha toccato "aggiorna": mandarlo nelle
     * impostazioni prima che abbia chiesto niente è il modo di far negare il
     * permesso per riflesso.
     */
    fun intentPermesso(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            // L'URI package: apre la schermata già sulla voce di QUESTA app,
            // non su un elenco generico dove l'utente deve trovarla da solo.
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * Scarica l'APK e lo consegna all'installatore di sistema.
     *
     * Il file va in `context.cacheDir`: nessun permesso di archiviazione, e il
     * sistema può ripulirlo. Si usa `PackageInstaller` con una sessione e non
     * `ACTION_INSTALL_PACKAGE`, che è deprecato da anni.
     *
     * [avanzamento] viene chiamato durante lo scaricamento con la percentuale
     * 0-100. Se il server non manda `Content-Length` non viene mai chiamato:
     * senza il totale non si può calcolare una percentuale.
     */
    suspend fun scaricaEInstalla(
        context: Context,
        versione: VersioneRemota,
        avanzamento: (Int) -> Unit,
    ): StatoInstallazione {
        // inutile scaricare 30 MB per poi scoprire che non si può installare
        if (!permessoConcesso(context)) return StatoInstallazione.PermessoMancante

        val file = File(context.cacheDir, "aggiornamento.apk")
        // cancella un eventuale scaricamento precedente interrotto
        file.delete()

        return try {
            scarica(file, versione, avanzamento)
            // la copia nella sessione è I/O bloccante: 30 MB da trasferire
            withContext(Dispatchers.IO) { installa(context, file) }
            // il PackageInstaller ha già ingerito tutto il contenuto: il file
            // locale è un doppione che non serve più
            file.delete()
            StatoInstallazione.InAttesaDiConferma
        } catch (e: Exception) {
            file.delete()
            StatoInstallazione.Errore(e.message ?: "errore sconosciuto")
        }
    }

    private suspend fun scarica(
        file: File,
        versione: VersioneRemota,
        avanzamento: (Int) -> Unit,
    ) {
        val client = HttpClient(OkHttp) {
            install(HttpTimeout) {
                // 5 minuti: 30 MB su una rete mobile lenta richiedono tempo.
                // Un timeout breve causerebbe errori intermittenti, non velocità.
                requestTimeoutMillis = 5 * 60_000L
                connectTimeoutMillis = 15_000L
            }
        }
        try {
            client.prepareGet(Aggiornamenti.urlApk(versione.apk)).execute { risposta ->
                val lunghezza = risposta.headers["Content-Length"]?.toLongOrNull()
                val canale = risposta.bodyAsChannel()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var scaricati = 0L
                file.outputStream().buffered().use { uscita ->
                    while (true) {
                        val letti = canale.readAvailable(buffer, 0, buffer.size)
                        // -1 = canale chiuso, download completato
                        if (letti == -1) break
                        if (letti > 0) {
                            uscita.write(buffer, 0, letti)
                            scaricati += letti
                            if (lunghezza != null && lunghezza > 0) {
                                avanzamento(
                                    minOf(((scaricati * 100) / lunghezza).toInt(), 100)
                                )
                            }
                        }
                    }
                }
            }
        } finally {
            client.close()
        }
    }

    private fun installa(context: Context, file: File) {
        val installatore = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installatore.createSession(params)
        installatore.openSession(sessionId).use { sessione ->
            sessione.openWrite("aggiornamento", 0, file.length()).use { uscita ->
                file.inputStream().use { it.copyTo(uscita) }
                sessione.fsync(uscita)
            }
            val intenzione = Intent(context, RicevitoreInstallazione::class.java)
            // FLAG_MUTABLE è obbligatorio da API 31: il sistema deve poter aggiungere
            // EXTRA_STATUS, EXTRA_STATUS_MESSAGE e EXTRA_INTENT all'intent prima di
            // consegnarlo al ricevitore. Sotto API 31 gli intent erano mutabili di
            // default e il flag non esiste come costante: si passa 0.
            val flagMutabile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val intentoSospeso = PendingIntent.getBroadcast(
                context, sessionId, intenzione, flagMutabile
            )
            sessione.commit(intentoSospeso.intentSender)
        }
    }
}
