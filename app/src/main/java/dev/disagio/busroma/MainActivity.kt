package dev.disagio.busroma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.disagio.busroma.ui.theme.TemaBusRoma

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disegna sotto le barre di sistema: su un telefono lo spazio in
        // altezza e' la risorsa scarsa, ed e' la stessa ragione per cui sul web
        // abbiamo tolto sottotitoli e intestazioni decorative.
        enableEdgeToEdge()
        setContent {
            TemaBusRoma {
                AppBusRoma()
            }
        }
    }
}
