package dev.disagio.busroma.percorsi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.disagio.busroma.mappa.GeometriaItinerario
import dev.disagio.busroma.mappa.MappaItinerario
import dev.disagio.busroma.mappa.geometriaDi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.dati.OpzioneAPiedi
import dev.disagio.busroma.dati.OpzioneItinerario
import dev.disagio.busroma.dati.TrattaAPiedi
import dev.disagio.busroma.dati.TrattaInMezzo
import dev.disagio.busroma.ui.DistintivoLinea
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Larghezza della colonna di sinistra: targhette e "a piedi" si incolonnano. */
private val COLONNA = 52.dp

/**
 * L'ora di un istante ISO, nel fuso di Roma.
 *
 * Il fuso è FISSATO a Europe/Rome e non preso dal telefono, come `oraLocale`
 * sul web: chi guarda gli autobus di Roma da un telefono con l'ora di New York
 * vuole sapere a che ora passa l'autobus a Roma.
 */
fun oraLocale(iso: String): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .format(Instant.parse(iso).atZone(ZoneId.of("Europe/Rome")))

/**
 * Il percorso scelto, a schermo intero.
 *
 * Prima, sul web, il dettaglio stava SOTTO l'elenco delle proposte: con
 * quattro o cinque itinerari finiva oltre la piega e chi non scorreva non lo
 * vedeva mai. Sostituisce l'elenco, e ci si torna con l'indietro di sistema —
 * che qui è il gesto naturale, mentre sul web andava rifatto a mano.
 */
@Composable
fun DettaglioItinerario(
    opzione: OpzioneItinerario,
    aPiedi: OpzioneAPiedi?,
    partenza: Pair<Double, Double>?,
    arrivo: Pair<Double, Double>?,
    indietro: () -> Unit,
    apriFermata: (String) -> Unit,
) {
    val c = LocalPalette.current

    // La geometria si costruisce all'APERTURA di questo itinerario e non con
    // l'elenco: servono due chiamate per ogni tratta in mezzo, e farle per
    // tutte e cinque le proposte significherebbe una ventina di richieste per
    // disegnarne una sola.
    var geometria by remember(opzione) { mutableStateOf<GeometriaItinerario?>(null) }
    LaunchedEffect(opzione) {
        geometria = try {
            geometriaDi(opzione, partenza, arrivo)
        } catch (e: Exception) {
            // La mappa e' un di piu': se non si costruisce, l'itinerario
            // scritto sotto risponde comunque alla domanda.
            null
        }
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "‹ Tutti i percorsi",
            style = MaterialTheme.typography.bodyMedium,
            color = c.brand600,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = indietro)
                .padding(vertical = 12.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${oraLocale(opzione.departAt)} → ${oraLocale(opzione.arriveAt)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = c.neutral900,
                    )
                    if (opzione.esempio) {
                        // "es." dichiarato e non sottinteso: gli orari sono di
                        // UNA corsa a titolo d'esempio, e senza questa sigla
                        // sembrano "la" partenza.
                        Text(
                            text = " es.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.neutral500,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
            }
            Text(
                text = "${opzione.durationMin} min di viaggio\n${opzione.walkMin} a piedi",
                style = MaterialTheme.typography.bodySmall,
                color = c.neutral600,
                textAlign = TextAlign.End,
            )
        }
        HorizontalDivider(color = c.neutral300)

        // Solo se copre TUTTO il viaggio: vedi GeometriaItinerario.completa.
        geometria?.takeIf { it.completa }?.let { g ->
            Spacer(Modifier.height(12.dp))
            MappaItinerario(
                geometria = g,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.height(12.dp))
        }

        opzione.legs.forEachIndexed { i, tratta ->
            if (i > 0) HorizontalDivider(color = c.neutral200)
            when (tratta) {
                is TrattaAPiedi -> TrattaPiedi(tratta, c)
                is TrattaInMezzo -> TrattaMezzo(tratta, c, apriFermata)
            }
        }

        aPiedi?.let {
            HorizontalDivider(color = c.neutral200)
            val km = "%.1f".format(it.meters / 1000.0)
            Text(
                text = buildString {
                    append("Oppure tutto a piedi in ${it.minutes} min ($km km)")
                    if (it.minutes < opzione.durationMin) append(", che è più rapido")
                    append(".")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = c.neutral600,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Text(
            text = "Orari da tabella, senza il tempo reale: un mezzo in ritardo cambia le " +
                "coincidenze. Verifica il passaggio sulla pagina della fermata.",
            style = MaterialTheme.typography.bodySmall,
            color = c.neutral500,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TrattaPiedi(t: TrattaAPiedi, c: Palette) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = "a piedi",
            style = MaterialTheme.typography.labelSmall,
            color = c.neutral500,
            modifier = Modifier.width(COLONNA),
        )
        Spacer(Modifier.width(12.dp))
        // Tre frasi diverse perché i tre casi sono diversi: si cammina VERSO
        // una fermata, DA una fermata verso la destinazione, oppure per tutto
        // il viaggio. Una formula sola ne direbbe una sbagliata due volte su
        // tre.
        val dove = when {
            t.to != null -> "fino a ${t.to.name}"
            t.from != null -> "da ${t.from.name} a destinazione"
            else -> "fino a destinazione"
        }
        val metri = t.meters?.let { " ($it m)" } ?: ""
        Text(
            text = "${t.minutes} min $dove$metri",
            style = MaterialTheme.typography.bodyMedium,
            color = c.neutral700,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrattaMezzo(t: TrattaInMezzo, c: Palette, apriFermata: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        DistintivoLinea(t.shortName, t.color, t.textColor, larghezza = COLONNA)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = t.headsign ?: "Destinazione non indicata",
                style = stileNome,
                fontWeight = FontWeight.Medium,
                color = c.neutral900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Salita e discesa su due righe e non su una: i nomi delle fermate
            // romane sono lunghi ("MONTE URANO/CAMERATA PICENA") e su una riga
            // sola venivano troncati entrambi, cioè si perdeva proprio il dato
            // che serve per scendere al punto giusto.
            CapoTratta("sali", t.from.name, oraLocale(t.departAt), c) { apriFermata(t.from.stopId) }
            CapoTratta("scendi", t.to.name, oraLocale(t.arriveAt), c) { apriFermata(t.to.stopId) }
        }
    }
}

@Composable
private fun CapoTratta(
    verbo: String,
    nome: String,
    ora: String,
    c: Palette,
    apri: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClick = apri),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = verbo,
            style = MaterialTheme.typography.labelSmall,
            color = c.neutral500,
            modifier = Modifier.width(44.dp),
        )
        Text(
            text = nome,
            style = MaterialTheme.typography.bodySmall,
            color = c.neutral700,
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = ora,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = c.neutral900,
        )
    }
}
