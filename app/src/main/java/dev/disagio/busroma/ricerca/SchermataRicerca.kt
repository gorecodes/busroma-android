package dev.disagio.busroma.ricerca

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import dev.disagio.busroma.dati.FermataTrovata
import dev.disagio.busroma.preferiti.FermataPreferita
import dev.disagio.busroma.preferiti.Preferiti as DepositoPreferiti
import dev.disagio.busroma.ui.Stella
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome
import kotlinx.coroutines.launch

/**
 * La schermata di partenza: ricerca di una fermata.
 *
 * Il titolo dice cosa c'è in pagina — "Fermate" — e non il nome dell'app: sul
 * web era l'unica delle quattro pagine a scrivere "Bus Roma" al posto del
 * proprio contenuto, ed è stato corretto. A chi l'app l'ha già aperta,
 * ripeterle il nome non dice niente.
 *
 * Nessun sottotitolo: il campo di ricerca dichiara già cosa fa.
 */
@Composable
fun SchermataRicerca(
    apriFermata: (stopId: String) -> Unit,
    apriPreferiti: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: RicercaViewModel = viewModel()
    val stato by vm.stato.collectAsStateWithLifecycle()
    val c = LocalPalette.current
    val contesto = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferiti by DepositoPreferiti.flusso(contesto).collectAsStateWithLifecycle(emptyList())

    Column(modifier.fillMaxSize().background(c.neutral100)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
            Text(
                text = "Fermate",
                style = MaterialTheme.typography.headlineMedium,
                color = c.neutral900,
            )
            Spacer(Modifier.height(10.dp))
            CampoRicerca(stato.testo, vm::scrivi, c)
        }

        Spacer(Modifier.height(12.dp))

        when {
            // A campo vuoto la schermata mostra i preferiti, non un
            // suggerimento: sono la cosa piu' utile al primo colpo perche' non
            // chiedono il permesso di posizione ne' una digitazione.
            stato.testo.isBlank() -> Column(
                Modifier.verticalScroll(rememberScrollState()),
            ) {
                Preferiti(preferiti, c, apriFermata, apriPreferiti)
                Spacer(Modifier.height(20.dp))
                SezioneVicine(apriFermata, c)
                Spacer(Modifier.height(24.dp))
            }
            stato.errore -> Nota("La ricerca non risponde.", c)
            stato.risultati.isEmpty() && !stato.cercando -> Nota("Nessuna fermata con questo nome.", c)
            else -> LazyColumn {
                items(stato.risultati, key = { it.stopId }) { f ->
                    RigaFermata(f, c) { apriFermata(f.stopId) }
                    HorizontalDivider(color = c.neutral200)
                }
            }
        }
    }
}

/**
 * Il campo di ricerca. `BasicTextField` e non `TextField` di Material: quello
 * porta con sé un'etichetta flottante, un'altezza minima di 56dp e
 * un'animazione che qui non servono a niente. Qui serve una riga in cui
 * scrivere.
 */
@Composable
private fun CampoRicerca(testo: String, scrivi: (String) -> Unit, c: Palette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.neutral50, RoundedCornerShape(4.dp))
            .border(1.dp, c.neutral300, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = testo,
            onValueChange = scrivi,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(
                MaterialTheme.typography.bodyLarge.copy(color = c.neutral900),
            ),
            cursorBrush = SolidColor(c.neutral900),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { campo ->
                if (testo.isEmpty()) {
                    Text(
                        "Cerca una fermata",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.neutral400,
                    )
                }
                campo()
            },
        )
    }
}

@Composable
private fun RigaFermata(f: FermataTrovata, c: Palette, apri: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = apri)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(
            text = f.name,
            style = stileNome,
            color = c.neutral900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            f.code?.let {
                Text(
                    text = "palina $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral500,
                )
            }
            if (f.routes.isNotEmpty()) {
                if (f.code != null) {
                    Text(" · ", style = MaterialTheme.typography.bodySmall, color = c.neutral400)
                }
                // Le linee che ci fermano: è l'informazione che fa scegliere fra
                // due paline con lo stesso nome ai due lati della strada.
                Text(
                    text = f.routes.take(6).joinToString(" ") +
                        if (f.routes.size > 6) " +${f.routes.size - 6}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = c.neutral600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Nota(testo: String, c: Palette) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(testo, style = MaterialTheme.typography.bodyMedium, color = c.neutral500)
    }
}

/**
 * I preferiti: illimitati e nell'ordine deciso dall'utente.
 *
 * Il riordino per trascinamento non c'e' ancora e il modello lo prevede gia'
 * (Preferiti.sposta): quando arrivera' non servira' migrare i dati di chi ha
 * l'app installata.
 *
 * La stella per rimuovere sta su ogni riga e non dentro un menu: e' l'unica
 * azione distruttiva qui, ed e' immediatamente annullabile ritoccandola dalla
 * pagina della fermata.
 */
@Composable
private fun Preferiti(
    elenco: List<FermataPreferita>,
    c: Palette,
    apri: (String) -> Unit,
    apriTutti: () -> Unit,
) {
    if (elenco.isEmpty()) {
        Nota(
            "Nessun preferito. Apri una fermata e tocca la stella: comparirà qui, " +
                "senza bisogno di cercarla ogni volta.",
            c,
        )
        return
    }

    // TRE, come sul web. La schermata iniziale deve stare sopra la piega: una
    // lista di quindici preferiti spingerebbe le fermate vicine fuori vista.
    val mostrati = elenco.take(3)
    val resto = elenco.size - mostrati.size

    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Preferiti",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = c.neutral500,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (resto > 0) "Vedi tutti (${elenco.size})" else "Gestisci",
                style = MaterialTheme.typography.bodySmall,
                color = c.neutral500,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = apriTutti)
                    .padding(horizontal = 10.dp, vertical = 13.dp),
            )
        }
        // Nessuna stella di rimozione qui: togliere un preferito e' un'azione
        // di gestione, e la gestione ha la sua schermata. In home la riga fa
        // una cosa sola, aprire la fermata.
        mostrati.forEach { f ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { apri(f.stopId) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(
                    text = f.nome,
                    style = stileNome,
                    color = c.neutral900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                f.palina?.let {
                    Text(
                        text = "palina $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.neutral500,
                    )
                }
            }
            HorizontalDivider(color = c.neutral200)
        }
    }
}
