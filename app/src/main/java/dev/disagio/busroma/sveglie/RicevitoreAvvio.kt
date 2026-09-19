package dev.disagio.busroma.sveglie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Riarma tutte le vigilanze dopo un riavvio o un aggiornamento del pacchetto.
 *
 * Le sveglie di AlarmManager NON sopravvivono al riavvio: un utente con una
 * vigilanza attiva che riavvia il telefono si troverebbe senza notifica senza
 * capire perché. Con MY_PACKAGE_REPLACED si copre anche l'aggiornamento
 * dell'app, dove il sistema cancella le sveglie dell'apk sostituito.
 * Una vigilanza persa in silenzio è peggio di una notifica mancata: l'utente
 * crede di essere coperto.
 */
class RicevitoreAvvio : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val azione = intent.action ?: return
        if (azione != Intent.ACTION_BOOT_COMPLETED && azione != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val risultato = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Sveglie.riarmaTutte(context.applicationContext)
            } finally {
                risultato.finish()
            }
        }
    }
}
