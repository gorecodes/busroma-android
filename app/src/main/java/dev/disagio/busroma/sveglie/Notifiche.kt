package dev.disagio.busroma.sveglie

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.disagio.busroma.MainActivity
import dev.disagio.busroma.R

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

    /**
     * Il nome dell'extra che trasporta lo stop_id dal PendingIntent a MainActivity.
     * È il contratto fra la notifica e la navigazione: definito qui, letto là.
     */
    const val EXTRA_FERMATA = "fermata"

    /** Idempotente, da chiamare prima di notificare e all'avvio dell'app. */
    fun creaCanale(context: Context) {
        val canale = NotificationChannel(
            CANALE_ARRIVI,
            "Arrivi in fermata",
            // Alta perché un avviso silenzioso che arriva quando il bus è già
            // passato non serve a niente: chi aspetta ha bisogno di sentirla.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avviso quando il bus sta arrivando alla fermata scelta"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(canale)
    }

    /** Vero se `POST_NOTIFICATIONS` è concesso (su Android 13+; prima, sempre vero). */
    fun permessoConcesso(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

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
    fun arrivo(context: Context, v: Vigilanza, minuti: Int, fresco: Boolean) {
        val headsign = v.headsign.orEmpty()
        val corpo = buildString {
            if (minuti == 0) {
                append("$headsign — in arrivo".trim())
            } else {
                append("$headsign — $minuti min".trim())
            }
            // Come la schermata degli arrivi che dice "questi dati non sono freschi":
            // si avvisa, ma si dichiara che il dato viene dall'ultimo controllo riuscito.
            if (!fresco) append(" · dato non aggiornato")
        }

        val apriIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_FERMATA, v.stopId)
        }
        val pending = PendingIntent.getActivity(
            context,
            // Richiesta stabile per la vigilanza: stessa chiave → stesso slot.
            v.chiave.hashCode(),
            apriIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notifica = NotificationCompat.Builder(context, CANALE_ARRIVI)
            .setSmallIcon(R.drawable.ic_notifica_arrivo)
            .setContentTitle("${v.shortName} in arrivo")
            .setContentText(corpo)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        // L'ID è lo stesso hashCode della chiave: idempotente per vigilanza.
        NotificationManagerCompat.from(context).notify(v.chiave.hashCode(), notifica)
    }
}
