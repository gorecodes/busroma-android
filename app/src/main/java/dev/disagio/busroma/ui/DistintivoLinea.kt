package dev.disagio.busroma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.ui.theme.LocalPalette

/**
 * La targhetta col numero della linea.
 *
 * Stava dentro la schermata degli arrivi e prendeva un `Arrivo`. Il
 * pianificatore mostra le stesse targhette su una `TrattaInMezzo`, che è un
 * altro tipo con gli stessi tre campi: invece di duplicare venti righe di
 * disegno, il composabile prende i tre campi e non sa da dove vengono.
 *
 * IL COLORE ARRIVA DAL FEED GTFS SOLO PER LE METROPOLITANE; per tutto il
 * resto è basalto, come deciso sul web — le linee di superficie non hanno un
 * colore ufficiale e inventarne uno sarebbe decorazione travestita da dato.
 *
 * Il ripiego usa la SCALA e non un esadecimale fisso, esattamente come
 * `routeBadgeStyle` sul web: in modalità scura la scala si ribalta, e un
 * basalto fisso coinciderebbe con lo sfondo facendo sparire la targhetta di
 * ogni autobus. I colori che arrivano dal GTFS restano letterali, perché sono
 * identità di linea.
 */
@Composable
fun DistintivoLinea(
    nome: String,
    colore: String?,
    coloreTesto: String?,
    modifier: Modifier = Modifier,
    larghezza: Dp = 46.dp,
) {
    val c = LocalPalette.current
    val fondo = colore?.let { coloreDaEsadecimale(it) } ?: c.neutral900
    val testo = coloreTesto?.let { coloreDaEsadecimale(it) } ?: c.neutral100
    Text(
        text = nome,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = testo,
        maxLines = 1,
        modifier = modifier
            .width(larghezza)
            .background(fondo, RoundedCornerShape(3.dp))
            .padding(vertical = 5.dp),
        textAlign = TextAlign.Center,
    )
}

/** "C4161C" o "#C4161C" dal feed GTFS a colore Compose. */
fun coloreDaEsadecimale(hex: String): Color? {
    val pulito = hex.removePrefix("#")
    if (pulito.length != 6) return null
    return try {
        Color(("ff$pulito").toLong(16))
    } catch (e: Exception) {
        null
    }
}
