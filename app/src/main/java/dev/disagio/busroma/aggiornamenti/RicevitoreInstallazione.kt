package dev.disagio.busroma.aggiornamenti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Riceve il risultato della sessione di PackageInstaller.
 *
 * Il caso critico è STATUS_PENDING_USER_ACTION: il sistema NON apre il dialogo
 * di conferma da solo, ma manda questo broadcast con l'intent che lo aprirà.
 * Senza questo passaggio l'installazione muore in silenzio — l'utente non vede
 * niente e pensa che il tasto non funzioni.
 *
 * FLAG_ACTIVITY_NEW_TASK è obbligatorio: si parte da un contesto di broadcast,
 * non da un'Activity, e Android lo richiede per avviare una nuova schermata.
 */
class RicevitoreInstallazione : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val conferma: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (conferma != null) {
                    conferma.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(conferma)
                }
            }
            // Gli altri esiti (SUCCESS, FAILURE, ecc.) non richiedono azione
            // da parte nostra: il dialogo dell'installatore sistema comunica
            // già l'esito all'utente.
        }
    }
}
