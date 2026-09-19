package dev.disagio.busroma.sveglie

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import dev.disagio.busroma.MainActivity

/**
 * Le sveglie del sistema operativo.
 *
 * QUESTA È LA CORREZIONE DI UN ERRORE. La prima versione usava `setAlarmClock`
 * credendola esente da permessi: non lo è. Da Android 12 sta nell'elenco delle
 * API di sveglia ESATTA insieme a `setExact` e `setExactAndAllowWhileIdle`, e
 * chiamarla senza permesso non degrada — solleva `SecurityException` e l'app si
 * chiude in mano all'utente. È esattamente quel che faceva.
 *
 * LA SVEGLIA ESATTA SERVE DAVVERO: l'ultimo controllo prima dell'arrivo è a un
 * minuto di distanza, e `setAndAllowWhileIdle` — l'unica inesatta che
 * attraversa il Doze — non viene consegnata più di una volta ogni nove minuti.
 * Con quella, la notifica dei cinque minuti arriverebbe a bus passato.
 *
 * Quindi si dichiara il permesso, in due forme:
 *
 * - `USE_EXACT_ALARM` da Android 13: concesso all'installazione, nessun
 *   dialogo, nessuna impostazione da cercare. Il Play Store lo consente solo
 *   alle app la cui funzione principale sono sveglie e promemoria, e qui la
 *   distribuzione è F-Droid, dove quella politica non esiste. Se un giorno si
 *   pubblicasse su Play, questa riga va rivista.
 * - `SCHEDULE_EXACT_ALARM` fino ad Android 12, dove `USE_EXACT_ALARM` non
 *   esiste ancora e questo è pre-concesso all'installazione.
 *
 * Il ripiego inesatto resta come rete di sicurezza — il permesso può essere
 * revocato da un'impostazione di sistema — e in quel caso la notifica può
 * arrivare tardi. Tardi è peggio che puntuale, ma è incomparabilmente meglio di
 * un'app che si chiude.
 *
 * `setAlarmClock` fra le esatte è comunque la scelta giusta: è la sola che il
 * sistema non rimanda nemmeno sotto restrizioni di batteria. Mostra l'icona
 * della sveglia in barra di stato mentre una vigilanza è pendente, ed è un
 * effetto collaterale onesto: dice che l'app sta aspettando qualcosa per te.
 */
object Sveglie {

    /**
     * Programma (o riprogramma) il controllo di una vigilanza.
     *
     * Il `PendingIntent` va derivato da [chiave] e usato con
     * `FLAG_UPDATE_CURRENT`, così riprogrammare non accumula sveglie: una
     * vigilanza, una sveglia.
     */
    fun programma(context: Context, chiave: String, istanteMs: Long) {
        val gestore = context.getSystemService<AlarmManager>() ?: return
        // L'icona nella barra apre MainActivity: chi ha un bus in arrivo
        // toccandola torna alla schermata, non finisce nel vuoto.
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val operazione = operazione(context, chiave)
        // Il controllo e la chiamata non sono atomici: il permesso può essere
        // revocato in mezzo, quindi la SecurityException si intercetta comunque
        // invece di fidarsi di canScheduleExactAlarms().
        if (esattePermesse(gestore)) {
            try {
                gestore.setAlarmClock(
                    AlarmManager.AlarmClockInfo(istanteMs, showIntent),
                    operazione,
                )
                return
            } catch (e: SecurityException) {
                // Si ripiega qui sotto.
            }
        }
        // Inesatta: in Doze non viene consegnata più di una volta ogni nove
        // minuti, quindi la notifica può arrivare in ritardo. Meglio tardi che
        // un'applicazione che si chiude.
        gestore.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, istanteMs, operazione)
    }

    private fun esattePermesse(gestore: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || gestore.canScheduleExactAlarms()

    fun annulla(context: Context, chiave: String) {
        context.getSystemService<AlarmManager>()?.cancel(operazione(context, chiave))
    }

    /**
     * Riarma tutte le vigilanze del deposito.
     *
     * Le sveglie NON sopravvivono al riavvio del telefono, quindi si richiama
     * da `BOOT_COMPLETED` e anche all'avvio dell'app, che è una rete di
     * sicurezza per il caso in cui il sistema le abbia perse per altre strade.
     * Deve essere idempotente.
     */
    suspend fun riarmaTutte(context: Context) {
        val adesso = System.currentTimeMillis()
        // MINIMO_MS e non un calcolo sull'ETA salvata: è un risveglio immediato
        // che si autoprogramma secondo la scala, più robusto che indovinare
        // l'istante giusto qui senza conoscere l'ETA fresco.
        Vigilanze.elenco(context).forEach { v ->
            programma(context, v.chiave, adesso + MINIMO_MS)
        }
    }

    /** Il PendingIntent che punta a [RicevitoreSveglia] con la [chiave] negli extra. */
    private fun operazione(context: Context, chiave: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            chiave.hashCode(),
            Intent(context, RicevitoreSveglia::class.java).putExtra("chiave", chiave),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
