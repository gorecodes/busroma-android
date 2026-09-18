package dev.disagio.busroma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Primo traguardo: verificare che la catena compila-installa-guarda funzioni
 * da capo a fondo. Nessuna rete e nessuna logica, cosi' se qualcosa si rompe
 * si sa che e' la configurazione di build e non il codice.
 *
 * Il contenuto vero arriva al passo successivo.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disegna sotto le barre di sistema: su un telefono moderno lo spazio
        // in altezza e' la risorsa scarsa, ed e' la stessa ragione per cui sul
        // web abbiamo tolto sottotitoli e intestazioni decorative.
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Avvio(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun Avvio(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Fermate",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Scheletro in piedi. Il contenuto arriva al prossimo passo.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
