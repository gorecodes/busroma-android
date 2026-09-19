package dev.disagio.busroma.percorsi

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.LuogoTrovato
import dev.disagio.busroma.posizione.Posizione
import dev.disagio.busroma.ui.Croce
import dev.disagio.busroma.ui.Palina
import dev.disagio.busroma.ui.Percorso
import dev.disagio.busroma.ui.Spillo
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.stileNome
import kotlinx.coroutines.delay

private val PERMESSI = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** La stessa attesa della ricerca fermate, per la stessa ragione. */
private const val ATTESA_MS = 250L

/** Larghezza dell'etichetta DA/A: le due righe devono incolonnarsi. */
private val COLONNA = 62.dp

/**
 * Un capo del viaggio: scelto, o da scegliere.
 *
 * DUE STATI NELLO STESSO COMPOSABILE, come sul web: quando il capo è scelto si
 * mostra una riga con il nome e una crocetta, quando non lo è si mostra il
 * campo di ricerca. Separarli in due composabili sembrava più pulito ma
 * obbligava chi legge a tenere a mente quale dei due è in scena.
 *
 * La ricerca interroga /api/geocode, che unisce le fermate del GTFS e i luoghi
 * di OpenStreetMap: per chi cerca sono la stessa cosa, "dove voglio andare".
 */
@Composable
fun CampoCapo(
    etichetta: String,
    valore: Capo?,
    cambia: (Capo?) -> Unit,
    conPosizione: Boolean,
    statoGps: StatoGps,
    chiediPosizione: () -> Unit,
) {
    val c = LocalPalette.current
    val contesto = LocalContext.current
    var testo by remember { mutableStateOf("") }
    var risultati by remember { mutableStateOf<List<LuogoTrovato>>(emptyList()) }

    val richiesta = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Concesso o negato si prova comunque: il ViewModel distingue i due
        // casi e mette il messaggio giusto.
        chiediPosizione()
    }

    // L'attesa prima di interrogare, con l'annullamento che risolve anche le
    // risposte fuori ordine: se "termi" parte lenta e "termini" arriva prima,
    // il LaunchedEffect della prima è già stato cancellato.
    LaunchedEffect(testo) {
        val q = testo.trim()
        if (q.length < 3) {
            risultati = emptyList()
            return@LaunchedEffect
        }
        delay(ATTESA_MS)
        risultati = try {
            Api.geocodifica(q).results.take(8)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // "Usa la mia posizione" sceglie il capo FUORI da questo composabile: è il
    // chiamante che, dopo il GPS, passa un valore nuovo tramite `valore`,
    // senza passare per nessuno dei clickable qui sotto. Senza questo effetto
    // testo e risultati restavano quelli della ricerca precedente, non visti
    // finché il capo non veniva tolto: la crocetta faceva ricomparire
    // l'elenco vecchio perché in quel caso `testo` non era mai cambiato.
    LaunchedEffect(valore) {
        if (valore != null) {
            testo = ""
            risultati = emptyList()
        }
    }

    if (valore != null) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Etichetta(etichetta)
            when (valore) {
                is Capo.MiaPosizione -> Spillo(c.brand500, Modifier.size(16.dp))
                is Capo.Fermata -> Palina(c.neutral500, Modifier.size(16.dp))
                is Capo.Luogo -> Percorso(c.neutral500, Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = valore.etichetta,
                style = stileNome,
                color = c.neutral900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        cambia(null)
                        // Azzerati qui e non solo nell'effetto sopra: quel
                        // campo torna in scena in questo stesso fotogramma,
                        // deve ricomparire pulito e non con le briciole di
                        // dieci minuti prima.
                        testo = ""
                        risultati = emptyList()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Croce(c.neutral400, Modifier.size(15.dp))
            }
        }
        HorizontalDivider(color = c.neutral300)
        return
    }

    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Etichetta(etichetta)
            BasicTextField(
                value = testo,
                onValueChange = { testo = it },
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = c.neutral900),
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.brand500),
                decorationBox = { campo ->
                    if (testo.isEmpty()) {
                        Text(
                            text = "Via, fermata o luogo",
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.neutral400,
                        )
                    }
                    campo()
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (conPosizione) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable {
                        if (Posizione.permessoConcesso(contesto)) chiediPosizione()
                        else richiesta.launch(PERMESSI)
                    }
                    .padding(start = COLONNA),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spillo(c.brand500, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (statoGps) {
                        StatoGps.Cerco -> "Cerco dove sei…"
                        StatoGps.Negato -> "Permesso negato"
                        StatoGps.NonTrovata -> "Non ti trovo, riprova"
                        StatoGps.Fermo -> "Usa la mia posizione"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.neutral600,
                )
            }
        }

        risultati.forEach { r ->
            val capo = r.aCapo()
            if (capo != null) {
                HorizontalDivider(color = c.neutral200)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable {
                            cambia(capo)
                            testo = ""
                            risultati = emptyList()
                        }
                        .padding(start = COLONNA, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (r.kind == "stop") Palina(c.neutral400, Modifier.size(15.dp))
                    else Percorso(c.neutral400, Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = r.label,
                            style = stileNome,
                            color = c.neutral900,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        r.detail?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = c.neutral500,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = c.neutral300)
    }
}

@Composable
private fun Etichetta(testo: String) {
    Text(
        text = testo.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = LocalPalette.current.neutral500,
        modifier = Modifier.width(COLONNA),
    )
}
