package dev.disagio.busroma.arrivi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.disagio.busroma.dati.Arrivo
import dev.disagio.busroma.preferiti.FermataPreferita
import dev.disagio.busroma.preferiti.Preferiti
import dev.disagio.busroma.ui.Stella
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome
import kotlinx.coroutines.launch

/**
 * Gli arrivi a una fermata: la schermata per cui esiste l'app.
 *
 * Le scelte di disposizione arrivano dal web, dove sono già state corrette
 * guardandole su uno schermo da 360 pixel:
 *
 * - la legenda del verde e del grigio si scrive UNA volta sola in testa, non
 *   su ogni riga: ripeterla raddoppiava l'altezza e dimezzava gli arrivi
 *   visibili;
 * - l'attesa sta in una colonna di larghezza fissa allineata a destra, così le
 *   cifre si incolonnano e la lista si scorre con l'occhio;
 * - la destinazione va troncata su una riga, perché i capolinea romani sono
 *   più lunghi di qualunque schermo.
 */
@Composable
fun SchermataArrivi(stopId: String, modifier: Modifier = Modifier) {
    val vm: ArriviViewModel = viewModel(key = stopId) { ArriviViewModel(stopId) }
    val stato by vm.stato.collectAsStateWithLifecycle()
    val c = LocalPalette.current
    val scope = rememberCoroutineScope()

    // Il ciclo gira solo a schermata in primo piano: fuori da qui la coroutine
    // viene annullata e l'app smette di interrogare le API dalla tasca.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(stopId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.ciclo() }
    }

    // I preferiti: si legge il flusso per sapere se questa fermata e' salvata.
    val contesto = LocalContext.current
    val preferiti by Preferiti.flusso(contesto).collectAsStateWithLifecycle(emptyList())
    val salvata = preferiti.any { it.stopId == stopId }

    Column(modifier.fillMaxSize().background(c.neutral100)) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stato.fermata?.name ?: "Fermata",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.neutral900,
                    maxLines = 2,
                )
                stato.fermata?.code?.let { palina ->
                    Text(
                        text = "palina $palina",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.neutral500,
                    )
                }
            }

            // La stella e' attiva solo quando il nome e' arrivato: salvare un
            // preferito chiamato "Fermata" non servirebbe a nessuno.
            stato.fermata?.let { f ->
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            scope.launch {
                                Preferiti.alterna(
                                    contesto,
                                    FermataPreferita(f.stopId, f.name, f.code),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Stella(
                        piena = salvata,
                        colore = if (salvata) c.brand500 else c.neutral400,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {

            if (stato.errore && stato.arrivi.isNotEmpty()) {
                // I dati restano, ma si dice che sono vecchi. Nascondere il
                // problema sarebbe peggio: chi aspetta deve sapere se il numero
                // che legge è fresco.
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Aggiornamento non riuscito: questi dati non sono freschi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.warn700,
                )
            }

            if (stato.arrivi.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "In verde i mezzi tracciati in tempo reale, in grigio l'orario previsto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral500,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            stato.primoCaricamento -> Caricamento(c)
            stato.arrivi.isEmpty() && stato.errore -> Messaggio(
                "Gli arrivi non arrivano.",
                c,
            ) { scope.launch { vm.riprova() } }
            stato.arrivi.isEmpty() -> Messaggio(
                "Niente. Il vuoto. Guardo 90 minuti avanti e non trovo nulla — più in là non so.",
                c,
                null,
            )
            else -> LazyColumn {
                items(stato.arrivi, key = { "${it.routeId}-${it.directionId}-${it.etaTs}" }) { a ->
                    RigaArrivo(a, stato.adesso, c)
                    HorizontalDivider(color = c.neutral200)
                }
            }
        }
    }
}

@Composable
private fun RigaArrivo(a: Arrivo, adesso: Long, c: Palette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DistintivoLinea(a, c)
        Spacer(Modifier.width(10.dp))
        Text(
            text = a.headsign ?: "Destinazione non indicata",
            style = stileNome,
            color = c.neutral900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Attesa(a, adesso, c)
    }
}

/**
 * Il numero della linea. Il colore arriva dal feed GTFS solo per le
 * metropolitane; per tutto il resto è basalto, come deciso sul web — le linee
 * di superficie non hanno un colore ufficiale e inventarne uno sarebbe
 * decorazione travestita da dato.
 *
 * Il ripiego usa la SCALA e non un esadecimale fisso, esattamente come
 * `routeBadgeStyle` sul web: in modalità scura la scala si ribalta, e un
 * basalto fisso coinciderebbe con lo sfondo facendo sparire la targhetta di
 * ogni autobus. I colori che arrivano dal GTFS restano letterali, perché sono
 * identità di linea.
 */
@Composable
private fun DistintivoLinea(a: Arrivo, c: Palette) {
    val fondo = a.color?.let { coloreDaEsadecimale(it) } ?: c.neutral900
    val testo = a.textColor?.let { coloreDaEsadecimale(it) } ?: c.neutral100
    Text(
        text = a.shortName,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = testo,
        maxLines = 1,
        modifier = Modifier
            .width(46.dp)
            .background(fondo, RoundedCornerShape(3.dp))
            .padding(vertical = 5.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/**
 * L'attesa: il dato per cui si apre l'app, quindi il numero è l'elemento più
 * grande della riga. Colonna a larghezza fissa perché le cifre si incolonnino.
 */
@Composable
private fun Attesa(a: Arrivo, adesso: Long, c: Palette) {
    val minuti = minutiDa(a.etaTs, adesso)
    val colore = if (a.isRealtime) c.live600 else c.neutral700
    Row(
        modifier = Modifier.width(76.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (a.isRealtime) {
            Box(Modifier.size(7.dp).background(c.live500, RoundedCornerShape(50)))
            Spacer(Modifier.width(5.dp))
        }
        when {
            minuti == null -> Text("—", style = MaterialTheme.typography.bodyMedium, color = c.neutral400)
            minuti <= 0 -> Text(
                "in arrivo",
                style = MaterialTheme.typography.bodyMedium,
                color = colore,
                fontWeight = FontWeight.SemiBold,
            )
            else -> Row(verticalAlignment = Alignment.Bottom) {
                Text("$minuti", style = MaterialTheme.typography.titleLarge, color = colore)
                Text(
                    " min",
                    style = MaterialTheme.typography.bodySmall,
                    color = colore,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun Caricamento(c: Palette) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = c.neutral400, strokeWidth = 2.dp)
    }
}

@Composable
private fun Messaggio(testo: String, c: Palette, riprova: (() -> Unit)?) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text(testo, style = MaterialTheme.typography.bodyLarge, color = c.neutral500)
        if (riprova != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Riprova",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.brand600,
                modifier = Modifier
                    // 44dp e' il minimo per un bersaglio da toccare col dito:
                    // la sola spaziatura del testo arrivava a 36.
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.brand50)
                    .clickable(onClick = riprova)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
    }
}

/** "C4161C" o "#C4161C" dal feed GTFS a colore Compose. */
private fun coloreDaEsadecimale(hex: String): androidx.compose.ui.graphics.Color? {
    val pulito = hex.removePrefix("#")
    if (pulito.length != 6) return null
    return try {
        androidx.compose.ui.graphics.Color(("ff$pulito").toLong(16))
    } catch (e: Exception) {
        null
    }
}
