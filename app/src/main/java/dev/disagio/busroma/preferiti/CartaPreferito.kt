package dev.disagio.busroma.preferiti

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.disagio.busroma.arrivi.minutiDa
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.Arrivo
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome
import kotlinx.coroutines.delay

/**
 * Trenta secondi e non quindici, come sul web: qui le schede sono tre, quindi
 * sono tre richieste per giro. Sulla schermata di una fermata, dove la
 * richiesta e' una sola e la si sta guardando fissa, il ritmo e' piu' stretto.
 */
private const val INTERVALLO_MS = 30_000L

/** Tre arrivi per scheda: oltre, la home non ci sta. */
private const val QUANTI = 3

/**
 * Un preferito in home, con i prossimi passaggi.
 *
 * MOSTRARE GLI ARRIVI E NON SOLO IL NOME e' il punto di tutta la sezione:
 * un elenco di nomi di fermata obbliga a toccare per sapere se conviene
 * uscire, e a quel punto tanto valeva cercarla. Con i tre prossimi passaggi la
 * risposta e' visibile senza toccare niente, che e' la ragione per cui i
 * preferiti esistono.
 *
 * Ogni scheda fa la sua richiesta: gli arrivi sono per fermata e non esiste un
 * endpoint che ne prenda diverse insieme. Con tre schede sono tre richieste
 * ogni trenta secondi, che e' esattamente quello che fa il web.
 */
@Composable
fun CartaPreferito(
    preferito: FermataPreferita,
    c: Palette,
    apri: () -> Unit,
) {
    var arrivi by remember(preferito.stopId) { mutableStateOf<List<Arrivo>?>(null) }
    var adesso by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(preferito.stopId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    arrivi = Api.arrivi(preferito.stopId).arrivals.take(QUANTI)
                } catch (e: Exception) {
                    // Si tengono i dati precedenti: a colpo d'occhio un dato di
                    // trenta secondi fa vale piu' di una scheda vuota. Se non
                    // c'era nulla, resta il puntino di attesa.
                }
                adesso = System.currentTimeMillis()
                delay(INTERVALLO_MS)
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = apri)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = preferito.nome,
                style = stileNome,
                fontWeight = FontWeight.SemiBold,
                color = c.neutral900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("›", style = MaterialTheme.typography.bodyLarge, color = c.neutral300)
        }

        Spacer(Modifier.size(3.dp))

        when {
            arrivi == null -> Text(
                "…",
                style = MaterialTheme.typography.bodyMedium,
                color = c.neutral400,
            )
            arrivi!!.isEmpty() -> Text(
                "Niente nei prossimi 90 minuti",
                style = MaterialTheme.typography.bodyMedium,
                color = c.neutral500,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                arrivi!!.forEach { a ->
                    RigaBreve(a, adesso, c)
                }
            }
        }
    }
}

@Composable
private fun RigaBreve(a: Arrivo, adesso: Long, c: Palette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = a.shortName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = a.textColor?.let { colore(it) } ?: c.neutral100,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .width(36.dp)
                .background(a.color?.let { colore(it) } ?: c.neutral900, RoundedCornerShape(2.dp))
                .padding(vertical = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = a.headsign ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = c.neutral500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        // Attesa ricalcolata da eta_ts, non il campo `minutes` del server: e'
        // un'istantanea e invecchia sullo schermo.
        val m = minutiDa(a.etaTs, adesso)
        val tinta = if (a.isRealtime) c.live600 else c.neutral700
        Row(
            Modifier.width(58.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (a.isRealtime) {
                Box(Modifier.size(5.dp).background(c.live500, RoundedCornerShape(50)))
                Spacer(Modifier.width(3.dp))
            }
            when {
                m == null -> Text("—", style = MaterialTheme.typography.bodySmall, color = c.neutral400)
                m <= 0 -> Text(
                    "ora",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tinta,
                )
                else -> Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$m",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = tinta,
                    )
                    Text(
                        " min",
                        style = MaterialTheme.typography.bodySmall,
                        color = tinta,
                    )
                }
            }
        }
    }
}

private fun colore(hex: String): Color? {
    val pulito = hex.removePrefix("#")
    if (pulito.length != 6) return null
    return try {
        Color(("ff$pulito").toLong(16))
    } catch (e: Exception) {
        null
    }
}
