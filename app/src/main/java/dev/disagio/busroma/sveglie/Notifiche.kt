package dev.disagio.busroma.sveglie

import android.content.Context

/**
 * La notifica dell'arrivo.
 *
 * Il testo è quello del web (`worker/push.ts`), perché è già stato scelto una
 * volta guardandolo su una lockscreen: titolo "117 in arrivo", corpo
 * "Termini — 5 min", e "in arrivo" quando i minuti sono zero. Al tocco si apre
 * la fermata: chi ha chiesto di essere avvisato sta per uscire, e la risposta
 * utile è l'elenco degli arrivi di quella palina, non la home.
 */
object Notifiche {

    /** Un canale solo, di importanza alta: è una notifica con una scadenza. */
    const val CANALE_ARRIVI = "arrivi"

    /** Idempotente, da chiamare prima di notificare e all'avvio dell'app. */
    fun creaCanale(context: Context): Unit = TODO()

    /** Vero se `POST_NOTIFICATIONS` è concesso (su Android 13+; prima, sempre vero). */
    fun permessoConcesso(context: Context): Boolean = TODO()

    /**
     * Mostra la notifica di arrivo.
     *
     * L'identificativo va derivato da [Vigilanza.chiave] e non generato: due
     * controlli della stessa vigilanza non devono impilare due notifiche.
     *
     * [fresco] falso significa che l'ETA viene dall'ultimo controllo riuscito e
     * non da adesso: il testo deve dirlo, invece di far passare per fresco un
     * dato di qualche minuto fa.
     */
    fun arrivo(context: Context, v: Vigilanza, minuti: Int, fresco: Boolean): Unit = TODO()
}
