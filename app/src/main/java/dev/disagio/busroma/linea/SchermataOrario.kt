package dev.disagio.busroma.linea

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.stileNome

/**
 * L'orario di una linea, a tutto schermo.
 *
 * STA IN UNA SCHERMATA SUA e non dentro la pagina della linea, e la ragione
 * l'ha decisa l'uso: infilato là, fra intestazione, selettore del verso, mappa
 * da 240dp e partenze, restava una striscia per l'orario e un'altra per
 * l'elenco delle fermate — tutto spremuto e niente leggibile. Cento o
 * trecento partenze vogliono una pagina, non un ritaglio.
 *
 * Nessun tasto indietro disegnato: su Android l'uscita è il gesto di sistema,
 * ed è la convenzione di tutte le altre schermate di questa app.
 */
@Composable
fun SchermataOrario(
    routeId: String,
    stopId: String,
    verso: Int,
    nomeFermata: String,
    shortName: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalPalette.current

    Column(modifier.fillMaxSize().background(c.neutral100)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shortName,
                style = MaterialTheme.typography.headlineSmall,
                color = c.neutral100,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .width(62.dp)
                    .background(c.neutral900, RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Partenze da",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral500,
                )
                Text(
                    text = nomeFermata,
                    style = stileNome,
                    fontWeight = FontWeight.SemiBold,
                    color = c.neutral900,
                    maxLines = 2,
                )
            }
        }

        OrarioCompleto(
            routeId = routeId,
            stopId = stopId,
            verso = verso,
            c = c,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
