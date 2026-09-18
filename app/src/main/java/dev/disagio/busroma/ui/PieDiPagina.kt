package dev.disagio.busroma.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.disagio.busroma.ui.theme.LocalPalette

/**
 * Il piè di pagina, in fondo alle schermate principali.
 *
 * L'INFORMATIVA DEVE ESSERE RAGGIUNGIBILE DA OGNI PAGINA, che è la stessa
 * ragione per cui sul web sta nel layout. Qui non c'è un layout condiviso che
 * scorre, quindi si aggiunge in coda alle quattro sezioni: sono quattro righe
 * di codice, e l'alternativa — nasconderla dietro un menu che non esiste —
 * significherebbe averla scritta per nessuno.
 *
 * Non sta nella barra in basso: quella ha quattro voci che si usano ogni
 * giorno, e una quinta per una pagina che si apre due volte l'anno
 * ruberebbe spazio al pollice.
 */
@Composable
fun PieDiPagina(apriInformazioni: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPalette.current
    val contesto = LocalContext.current

    HorizontalDivider(color = c.neutral200, modifier = modifier.padding(top = 8.dp))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Voce("Offrimi un caffè", c.neutral500) {
            contesto.startActivity(Intent(Intent.ACTION_VIEW, "https://ko-fi.com/codingpao".toUri()))
        }
        Voce("Privacy e licenze", c.neutral500, apriInformazioni)
    }
}

@Composable
private fun Voce(
    testo: String,
    colore: androidx.compose.ui.graphics.Color,
    apri: () -> Unit,
) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodySmall,
        color = colore,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = apri)
            .padding(vertical = 14.dp),
    )
}
