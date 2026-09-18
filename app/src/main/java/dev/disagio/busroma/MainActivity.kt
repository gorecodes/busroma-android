package dev.disagio.busroma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import dev.disagio.busroma.arrivi.SchermataArrivi
import dev.disagio.busroma.ui.theme.TemaBusRoma

/**
 * Fase 2: la fermata e' fissata nel codice.
 *
 * La ricerca e i preferiti sono la fase 3: qui serve provare la catena
 * completa - rete, deserializzazione, stato, aggiornamento periodico,
 * gestione degli errori - su una fermata vera, senza mescolarci anche la
 * navigazione.
 *
 * 70286 = MILANO/NAZIONALE, scelta perche' e' servita da piu' linee di
 * superficie con e senza tracciamento in tempo reale, quindi mostra in un
 * colpo la distinzione fra il verde e il grigio.
 */
private const val FERMATA_DI_PROVA = "70286"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disegna sotto le barre di sistema: su un telefono lo spazio in
        // altezza e' la risorsa scarsa, ed e' la stessa ragione per cui sul web
        // abbiamo tolto sottotitoli e intestazioni decorative.
        enableEdgeToEdge()
        setContent {
            TemaBusRoma {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    SchermataArrivi(
                        stopId = FERMATA_DI_PROVA,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}
