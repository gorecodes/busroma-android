package dev.disagio.busroma.aggiornamenti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            PackageInstaller.STATUS_SUCCESS -> {
                // Niente da fare: il processo viene sostituito da quello
                // della nuova versione, e il banner sparisce da sé grazie al
                // filtro sul versionCode in Aggiornamenti.flussoDisponibile.
            }
            else -> {
                // Tutti gli altri esiti (l'utente ha rifiutato il dialogo di
                // conferma, o l'installazione è fallita) prima di questo
                // commit sparivano nel nulla: il banner restava impiccato
                // sull'ultimo stato mostrato, come se il tasto non avesse
                // fatto niente. Un BroadcastReceiver non ha un composabile a
                // cui parlare, quindi si scrive sul DataStore che il banner
                // legge come flusso.
                //
                // goAsync() perché onReceive deve tornare subito ma la
                // scrittura su DataStore è sospesa: senza, il sistema
                // potrebbe considerare il ricevitore terminato e uccidere il
                // processo a metà scrittura.
                val risultato = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Aggiornamenti.segnaInstallazioneNonRiuscita(context.applicationContext)
                    } finally {
                        risultato.finish()
                    }
                }
            }
        }
    }
}
