package dev.disagio.busroma.avvisi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.Avviso
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette

/**
 * Gli avvisi di servizio, divisi in due.
 *
 * DUE SEZIONI E NON UNA LISTA ORDINATA, come sul web: un cantiere di dieci
 * mesi e una manifestazione di oggi messi nella stessa lista si leggono come
 * la stessa cosa, e non lo sono. La distinzione la fa il server (durata sotto
 * i due giorni = urgente), il client la mostra.
 */
@Composable
fun SchermataAvvisi(modifier: Modifier = Modifier) {
    val c = LocalPalette.current
    var avvisi by remember { mutableStateOf<List<Avviso>?>(null) }
    var errore by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            avvisi = Api.avvisi().avvisi
        } catch (e: Exception) {
            errore = true
        }
    }

    Column(modifier.fillMaxSize().background(c.neutral100)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp)) {
            Text(
                text = "Avvisi",
                style = MaterialTheme.typography.headlineMedium,
                color = c.neutral900,
            )
            Text(
                text = "Deviazioni, sospensioni e modifiche di percorso dichiarate da ATAC. " +
                    "Prima quelle di oggi, poi i cantieri che vanno avanti da mesi.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.neutral500,
            )
        }

        val elenco = avvisi
        when {
            errore -> Nota("Gli avvisi non si fanno trovare.", c)
            elenco == null -> Nota("Carico...", c)
            elenco.isEmpty() -> Nota(
                "Nessun avviso attivo. Cosa che a Roma succede raramente, quindi godiamocela.",
                c,
            )
            else -> {
                val urgenti = elenco.filter { it.urgente }
                val strutturali = elenco.filterNot { it.urgente }
                LazyColumn {
                    if (urgenti.isNotEmpty()) {
                        item { Titoletto("Oggi", c) }
                        items(urgenti, key = { "u-" + it.id }) { a -> Urgente(a, c) }
                    }
                    if (strutturali.isNotEmpty()) {
                        item {
                            Column {
                                Spacer(Modifier.height(14.dp))
                                Titoletto("Cantieri e modifiche di lungo periodo", c)
                                Text(
                                    text = "Vanno avanti da settimane o mesi: utile saperlo una " +
                                        "volta, inutile essere avvisati ogni giorno.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.neutral500,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                )
                            }
                        }
                        items(strutturali, key = { "s-" + it.id }) { a ->
                            Strutturale(a, c)
                            HorizontalDivider(color = c.neutral200)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** Oggi: riquadro ambrato, aperto. E' la notizia. */
@Composable
private fun Urgente(a: Avviso, c: Palette) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(c.warn50, RoundedCornerShape(5.dp))
            .border(1.dp, c.warn300, RoundedCornerShape(5.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = listOfNotNull(a.effetto, a.causa, a.quando).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = c.warn700,
        )
        Text(
            text = a.titolo,
            style = MaterialTheme.typography.bodyMedium,
            color = c.warn700,
        )
        a.dettaglio?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = c.warn600,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Linee(a.linee, c)
    }
}

/** Lungo periodo: una riga sobria. E' contesto, non allarme. */
@Composable
private fun Strutturale(a: Avviso, c: Palette) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(
            text = a.titolo,
            style = MaterialTheme.typography.bodyMedium,
            color = c.neutral900,
        )
        Text(
            text = listOfNotNull(a.effetto, a.causa?.let { "per $it" }, a.quando)
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = c.neutral500,
        )
        Linee(a.linee, c)
    }
}

/** Le linee coinvolte. Oltre otto si tronca: e' un dettaglio, non il contenuto. */
@Composable
private fun Linee(linee: List<String>, c: Palette) {
    if (linee.isEmpty()) return
    val mostrate = linee.take(8)
    val resto = linee.size - mostrate.size
    Text(
        text = mostrate.joinToString(" ") + if (resto > 0) " +$resto" else "",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = c.neutral600,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * Etichetta di sezione, nel colore d'identità.
 *
 * Il colore qui non è decorazione: le etichette di sezione sono
 * l'intestazione della struttura, non un dato del trasporto, e sono il posto
 * dove il porpora istituzionale può comparire senza dire niente di falso.
 */
@Composable
private fun Titoletto(testo: String, c: Palette) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = c.brand500,
        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp),
    )
}

@Composable
private fun Nota(testo: String, c: Palette) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodyMedium,
        color = c.neutral500,
        modifier = Modifier.padding(16.dp),
    )
}
