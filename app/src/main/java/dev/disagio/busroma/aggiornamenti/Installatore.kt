package dev.disagio.busroma.aggiornamenti

import android.content.Context
import android.content.Intent

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
    fun permessoConcesso(context: Context): Boolean = TODO()

    /**
     * L'intento che porta alla schermata di sistema dove si concede.
     *
     * Va aperto solo dopo che l'utente ha toccato "aggiorna": mandarlo nelle
     * impostazioni prima che abbia chiesto niente è il modo di far negare il
     * permesso per riflesso.
     */
    fun intentPermesso(context: Context): Intent = TODO()

    /**
     * Scarica l'APK e lo consegna all'installatore di sistema.
     *
     * Il file va in `context.cacheDir` o nella cartella privata dell'app:
     * nessun permesso di archiviazione, e il sistema può ripulirlo. Si usa
     * `PackageInstaller` con una sessione e non `ACTION_INSTALL_PACKAGE`, che è
     * deprecato da anni.
     *
     * [avanzamento] viene chiamato durante lo scaricamento per muovere la barra.
     */
    suspend fun scaricaEInstalla(
        context: Context,
        versione: VersioneRemota,
        avanzamento: (Int) -> Unit,
    ): StatoInstallazione = TODO()
}
