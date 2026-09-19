package dev.disagio.busroma.sveglie

import android.content.Context

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
    fun programma(context: Context, chiave: String, istanteMs: Long): Unit = TODO()

    fun annulla(context: Context, chiave: String): Unit = TODO()

    /**
     * Riarma tutte le vigilanze del deposito.
     *
     * Le sveglie NON sopravvivono al riavvio del telefono, quindi si richiama
     * da `BOOT_COMPLETED` e anche all'avvio dell'app, che è una rete di
     * sicurezza per il caso in cui il sistema le abbia perse per altre strade.
     * Deve essere idempotente.
     */
    suspend fun riarmaTutte(context: Context): Unit = TODO()
}
