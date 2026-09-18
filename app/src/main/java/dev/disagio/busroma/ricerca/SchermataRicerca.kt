package dev.disagio.busroma.ricerca

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import dev.disagio.busroma.dati.FermataTrovata
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome

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
    modifier: Modifier = Modifier,
) {
    val vm: RicercaViewModel = viewModel()
    val stato by vm.stato.collectAsStateWithLifecycle()
    val c = LocalPalette.current

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
            stato.testo.isBlank() -> Nota(
                "Cerca per nome o per numero di palina. I preferiti arrivano al prossimo passo.",
                c,
            )
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
