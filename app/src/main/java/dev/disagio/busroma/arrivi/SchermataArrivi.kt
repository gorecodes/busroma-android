package dev.disagio.busroma.arrivi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.disagio.busroma.ui.AzioniIntestazione
import dev.disagio.busroma.dati.Avviso
import androidx.compose.ui.draw.drawBehind
import dev.disagio.busroma.ui.Triangolo
import dev.disagio.busroma.ui.DistintivoLinea
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
fun SchermataArrivi(
    stopId: String,
    apriCorsa: (String) -> Unit,
    apriLinea: (String, Int?) -> Unit,
    apriAvvisi: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

    /**
     * Quale avviso è aperto: UNO SOLO alla volta, e la chiave è quella della
     * riga. Tenere un booleano per riga avrebbe lasciato lo schermo pieno di
     * pannelli aperti, e l'elenco degli arrivi serve a scorrere i numeri.
     */
    var apertoId by remember(stopId) { mutableStateOf<String?>(null) }

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
            AzioniIntestazione(apriAvvisi)
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
                // L'indice di posizione disambigua mezzi accodati o corse da
                // tabella con lo stesso etaTs: senza di lui Compose solleva
                // IllegalArgumentException su chiavi duplicate.
                itemsIndexed(stato.arrivi, key = { idx, it -> "$idx-${it.routeId}-${it.directionId}-${it.etaTs}" }) { idx, a ->
                    val chiave = "$idx-${a.routeId}-${a.directionId}-${a.etaTs}"
                    RigaArrivo(
                        a = a,
                        adesso = stato.adesso,
                        c = c,
                        suoiAvvisi = stato.avvisiPerLinea[a.shortName],
                        aperto = apertoId == chiave,
                        alternaAvviso = { apertoId = if (apertoId == chiave) null else chiave },
                        // LA RIGA PORTA SEMPRE DA QUALCHE PARTE, come sul web:
                        // alla corsa se il mezzo e' tracciato, altrimenti alla
                        // linea nel verso di questo arrivo.
                        apri = {
                            if (a.tripId != null) apriCorsa(a.tripId)
                            else apriLinea(a.routeId, a.directionId)
                        },
                    )
                    HorizontalDivider(color = c.neutral200)
                }
            }
        }
    }
}

/**
 * Una riga di arrivo, con l'avviso di servizio della sua linea se c'è.
 *
 * IL TRIANGOLO STA A SINISTRA, subito dopo la targhetta, e non in fondo alla
 * riga: sul web era stato provato dall'altra parte e la riga diventava
 * illeggibile - minuti, campanella e triangolo tutti addossati a destra. A
 * sinistra il triangolo sta accanto alla cosa che qualifica, cioè la linea.
 *
 * L'avviso si APRE SOTTO LA RIGA invece di stare in un riquadro in cima alla
 * schermata: la domanda è "la MIA linea ha problemi", e un pannello generale
 * la lascia senza risposta costringendo a leggere per capire se riguarda te.
 */
@Composable
private fun RigaArrivo(
    a: Arrivo,
    adesso: Long,
    c: Palette,
    suoiAvvisi: List<Avviso>?,
    aperto: Boolean,
    alternaAvviso: () -> Unit,
    apri: () -> Unit,
) {
    Column(Modifier.background(if (aperto) c.warn50 else c.neutral100)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // SEMPRE CLICCABILE.
            //
            // Prima il tocco era abilitato solo con un trip_id, ragionando che
            // senza mezzo tracciato non ci fosse una corsa da aprire. Era un
            // errore: sul web la riga senza trip_id porta alla LINEA nel verso
            // dell'arrivo, quindi una destinazione c'e' sempre. E l'errore si
            // e' visto nel momento peggiore - col tempo reale di ATAC giu',
            // nessun arrivo ha un trip_id e l'intera lista diventava inerte su
            // ogni fermata dell'app.
            .clickable(onClick = apri)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DistintivoLinea(a.shortName, a.color, a.textColor)
        if (suoiAvvisi != null) {
            // Area di tocco da 44dp attorno a un glifo da 15: il triangolo è
            // piccolo per non urlare, ma il bersaglio deve essere un dito.
            Box(
                Modifier
                    .size(width = 30.dp, height = 44.dp)
                    .clickable(onClick = alternaAvviso),
                contentAlignment = Alignment.Center,
            ) {
                Triangolo(
                    if (aperto) c.warn700 else c.warn600,
                    Modifier.size(15.dp),
                )
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
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

    if (aperto && suoiAvvisi != null) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suoiAvvisi.forEach { av ->
                Column(
                    Modifier
                        .drawBehind {
                            // Filetto verticale a sinistra: lega il testo alla
                            // riga da cui è uscito. Disegnato invece che messo
                            // con un Box, così non entra nel flusso.
                            drawRect(
                                color = c.warn400,
                                size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height),
                            )
                        }
                        .padding(start = 10.dp),
                ) {
                    Text(
                        // SI SA CHE LA LINEA È COINVOLTA, non che lo sia questa
                        // fermata: ATAC dichiara gli stop_ids in 3 avvisi su
                        // 181. Dirlo con esattezza evita di far scendere
                        // qualcuno dove il mezzo passa regolarmente.
                        text = buildString {
                            append(if (av.toccaQui) "${av.effetto} qui" else "${av.effetto} su un tratto del percorso")
                            av.causa?.let { append(" · $it") }
                            av.quando?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = c.warn700,
                    )
                    Text(
                        text = av.titolo,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.neutral600,
                    )
                    av.dettaglio?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.neutral500,
                        )
                    }
                }
            }
        }
    }
    }
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
