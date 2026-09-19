package dev.disagio.busroma.sveglie

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import dev.disagio.busroma.MainActivity

/**
 * Le sveglie del sistema operativo.
 *
 * SI USA `setAlarmClock` E NON `setExactAndAllowWhileIdle`: la prima è esatta,
 * attraversa il Doze e non richiede alcun permesso; la seconda da Android 13
 * pretende `SCHEDULE_EXACT_ALARM`, che l'utente deve concedere a mano dalle
 * impostazioni. `setAndAllowWhileIdle`, l'altra senza permessi, in Doze non
 * scatta più di una volta ogni nove minuti, quindi non può servire l'ultimo
 * minuto prima dell'arrivo. Il prezzo è l'icona della sveglia in barra di
 * stato mentre una vigilanza è pendente: è un effetto collaterale onesto.
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
        gestore.setAlarmClock(
            AlarmManager.AlarmClockInfo(istanteMs, showIntent),
            operazione(context, chiave),
        )
    }

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
