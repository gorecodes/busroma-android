package dev.disagio.busroma.sveglie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Riceve la sveglia di AlarmManager e lancia il controllo della vigilanza.
 *
 * [goAsync] è obbligatorio: senza, il sistema considera il ricevitore
 * concluso a fine di [onReceive] e può riaddormentare il telefono nel mezzo
 * della richiesta di rete. Il budget è circa dieci secondi; [withTimeoutOrNull]
 * ne usa nove — un margine che copre l'8 s di timeout di [Api] più la
 * consegna — e in caso di scadenza riprogramma un controllo fra 60 secondi
 * invece di perdere la vigilanza in silenzio.
 */
class RicevitoreSveglia : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chiave = intent.getStringExtra("chiave") ?: return
        val risultato = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val completato = withTimeoutOrNull(9_000) {
                    Avvisami.controlla(context.applicationContext, chiave)
                }
                if (completato == null) {
                    // Il budget del broadcast è scaduto: la rete era lenta o
                    // bloccata. Si riprogramma invece di perdere la vigilanza.
                    val adesso = System.currentTimeMillis()
                    Sveglie.programma(context.applicationContext, chiave, adesso + 60_000L)
                }
            } finally {
                risultato.finish()
            }
        }
    }
}
