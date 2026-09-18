package dev.disagio.busroma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.TemaBusRoma
import dev.disagio.busroma.ui.theme.stileNome

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disegna sotto le barre di sistema: su un telefono lo spazio in
        // altezza è la risorsa scarsa, ed è la stessa ragione per cui sul web
        // abbiamo tolto sottotitoli e intestazioni decorative.
        enableEdgeToEdge()
        setContent {
            TemaBusRoma {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Campionario(Modifier.padding(padding))
                }
            }
        }
    }
}

/**
 * Schermata di verifica della fase 1, da buttare appena arrivano gli arrivi.
 *
 * Esercita di proposito i punti dove i colori possono sbagliare: la superficie
 * in rilievo contro lo sfondo (in scuro il 50 non si ribalta, e sbagliarlo fa
 * sembrare le schede incassate), i tre livelli di testo, e i tre colori che
 * portano significato.
 */
@Composable
private fun Campionario(modifier: Modifier = Modifier) {
    val c = LocalPalette.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.neutral100)
            .padding(16.dp),
    ) {
        Text("Fermate", style = MaterialTheme.typography.headlineMedium, color = c.neutral900)
        Text(
            "Fase 1: palette e barre di sistema",
            style = MaterialTheme.typography.bodyMedium,
            color = c.neutral500,
        )

        Spacer(Modifier.height(16.dp))

        // Superficie in rilievo: deve staccarsi dallo sfondo in ENTRAMBI i temi.
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.neutral50, RoundedCornerShape(6.dp))
                .border(1.dp, c.neutral300, RoundedCornerShape(6.dp))
                .padding(12.dp),
        ) {
            // Nome di luogo: carattere condensato. Il confronto col testo
            // accanto e' il punto della prova.
            Text(
                "C.SO VITTORIO EMANUELE/S. A. DELLA VALLE",
                style = stileNome,
                color = c.neutral900,
                maxLines = 1,
            )
            Text("palina 70003", style = MaterialTheme.typography.bodySmall, color = c.neutral500)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.neutral200)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pallino(c.live500)
                Text(
                    "  4 min",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.live600,
                )
                Text(
                    "   tracciato",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral400,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "  12 min",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.neutral700,
                )
                Text(
                    "   previsto",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral400,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // I tre colori che significano qualcosa.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tessera("vivo", c.live500, Color.White)
            Tessera("ATAC", c.brand500, Color.White)
            Tessera("avviso", c.warn500, Color.White)
        }

        Spacer(Modifier.height(12.dp))

        // Avviso di servizio: in scuro è il caso che sul web si era rotto.
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.warn50, RoundedCornerShape(6.dp))
                .border(1.dp, c.warn300, RoundedCornerShape(6.dp))
                .padding(10.dp),
        ) {
            Text(
                "Deviata per manifestazione · in corso",
                style = MaterialTheme.typography.bodySmall,
                color = c.warn700,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "PIAZZA VENEZIA: CANTIERI STAZIONE METRO C",
                style = MaterialTheme.typography.bodySmall,
                color = c.neutral600,
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Scala neutra, dal fondo all'inchiostro",
            style = MaterialTheme.typography.bodySmall,
            color = c.neutral500,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                c.neutral50, c.neutral100, c.neutral200, c.neutral300, c.neutral400,
                c.neutral500, c.neutral600, c.neutral700, c.neutral900, c.neutral950,
            ).forEach { colore ->
                Column(
                    Modifier
                        .size(28.dp)
                        .background(colore, RoundedCornerShape(3.dp))
                        .border(1.dp, c.neutral300, RoundedCornerShape(3.dp)),
                ) {}
            }
        }
    }
}

@Composable
private fun Pallino(colore: Color) {
    Column(Modifier.size(7.dp).background(colore, RoundedCornerShape(50))) {}
}

@Composable
private fun Tessera(etichetta: String, fondo: Color, testo: Color) {
    Text(
        text = etichetta,
        style = MaterialTheme.typography.labelMedium,
        color = testo,
        modifier = Modifier
            .background(fondo, RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
