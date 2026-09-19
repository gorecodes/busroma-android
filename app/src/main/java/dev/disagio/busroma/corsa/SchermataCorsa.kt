package dev.disagio.busroma.corsa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.FermataCorsa
import dev.disagio.busroma.dati.Linea
import dev.disagio.busroma.dati.MezzoCorsa
import dev.disagio.busroma.ui.AzioniIntestazione
import androidx.compose.ui.draw.clip
import dev.disagio.busroma.mappa.MappaPercorso
import dev.disagio.busroma.mappa.PuntoFermata
import dev.disagio.busroma.mappa.PuntoMezzo
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val INTERVALLO_MS = 15_000L

/** Roma e non il fuso del telefono: gli orari sono di un servizio romano. */
private val FUSO = ZoneId.of("Europe/Rome")
private val ORA_MINUTI = DateTimeFormatter.ofPattern("HH:mm").withZone(FUSO)

/**
 * Una singola corsa: dove e' arrivata e dove deve ancora passare.
 *
 * E' la schermata che risponde a "il mio autobus dov'e'". La pagina della
 * linea dice dove sono TUTTI i mezzi; questa segue UNO, quello che sta
 * arrivando alla fermata da cui si e' toccato l'arrivo.
 *
 * MEZZO FANTASMA. Quando `vehicle` e' nullo la corsa esiste in tabella ma
 * nessun mezzo la sta segnalando. Dirlo con quelle parole - come sul web - e'
 * meglio che mostrare una pagina identica a quella di un mezzo tracciato: chi
 * aspetta deve sapere se sta guardando un orario o un autobus.
 */
@Composable
fun SchermataCorsa(
    tripId: String,
    apriFermata: (String) -> Unit,
    apriAvvisi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPalette.current

    var linea by remember(tripId) { mutableStateOf<Linea?>(null) }
    var mezzo by remember(tripId) { mutableStateOf<MezzoCorsa?>(null) }
    var fermate by remember(tripId) { mutableStateOf<List<FermataCorsa>>(emptyList()) }
    var destinazione by remember(tripId) { mutableStateOf<String?>(null) }
    var caricata by remember(tripId) { mutableStateOf(false) }
    var errore by remember(tripId) { mutableStateOf(false) }
    /** Il verso dichiarato dalla corsa: serve a chiedere il tracciato giusto. */
    var verso by remember(tripId) { mutableStateOf<Int?>(null) }
    var tracciato by remember(tripId) { mutableStateOf<List<List<Double>>?>(null) }
    var adesso by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tripId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    val r = Api.corsa(tripId)
                    linea = r.route
                    mezzo = r.vehicle
                    fermate = r.stops
                    destinazione = r.headsign
                    verso = r.directionId
                    errore = false
                } catch (e: Exception) {
                    // Si tengono i dati precedenti, come altrove: una corsa
                    // che scompare per un buco di rete sembrerebbe finita.
                    errore = true
                }
                caricata = true
                adesso = System.currentTimeMillis()
                delay(INTERVALLO_MS)
            }
        }
    }

    // IL TRACCIATO DELLA LINEA, letto UNA VOLTA e non a ogni giro di
    // aggiornamento: sono qualche centinaio di punti che non cambiano mentre
    // guardi la corsa. La chiave e' linea piu' verso, quindi se la corsa
    // cambia si rilegge da se'.
    val routeId = linea?.routeId
    LaunchedEffect(routeId, verso) {
        val r = routeId ?: return@LaunchedEffect
        tracciato = try {
            // Verso assente: si tenta lo zero, che e' quello che le linee
            // romane dichiarano quando ne hanno uno solo.
            Api.fermateLinea(r, verso ?: 0).shape?.coordinates
        } catch (e: Exception) {
            // Senza tracciato la mappa mostra comunque fermate e mezzo: e'
            // esattamente come si comportava prima di questa aggiunta.
            null
        }
    }

    // La prima fermata ancora da servire: da lei in su e' passato.
    val prossima = remember(fermate, adesso) {
        fermate.indexOfFirst { f -> (minutiA(f.etaTs, adesso) ?: -1) >= 0 }
    }

    val stato = rememberLazyListState()
    // Si scorre alla fermata corrente, una volta sola: su una corsa di
    // cinquanta fermate, aprire la pagina in cima significa non vedere il
    // punto che interessa.
    var giaScorso by remember(tripId) { mutableStateOf(false) }
    LaunchedEffect(prossima, fermate.size) {
        if (!giaScorso && prossima > 2 && fermate.isNotEmpty()) {
            stato.scrollToItem((prossima - 2).coerceAtLeast(0))
            giaScorso = true
        }
    }

    Column(modifier.fillMaxSize().background(c.neutral100)) {
        Intestazione(linea, mezzo, destinazione, adesso, c, apriAvvisi)

        // La mappa della corsa: CON IL TRACCIATO, al contrario del web.
        //
        // DECISIONE ROVESCIATA GUARDANDO LO SCHERMO. Prima era senza, con la
        // motivazione che qui interessa dove sta il mezzo rispetto alle
        // fermate che gli restano e che il filo del percorso si ha nella
        // pagina della linea. Sul telefono quella scelta non regge: una
        // ventina di pallini sparsi su una mappa senza niente che li unisca
        // non si legge come una scelta, si legge come una mappa che non ha
        // finito di caricare — ed e' stata segnalata come tale.
        //
        // Il tracciato e' quello del VERSO della linea, non della singola
        // corsa: su una corsa variante puo' discostarsi per un tratto. Meglio
        // un filo che passa per la strada giusta quasi sempre che nessun filo
        // mai.
        //
        // Piu' bassa che nella pagina della linea (220 contro 240) perche'
        // sotto c'e' una lista di cinquanta fermate che e' il pezzo forte di
        // questa schermata.
        if (fermate.isNotEmpty() || mezzo != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(220.dp)
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                MappaPercorso(
                    fermate = fermate
                        // Il feed non copre sempre tutte le fermate con le
                        // coordinate: quelle a zero finirebbero nel Golfo di
                        // Guinea e l'inquadratura comprenderebbe mezzo mondo.
                        .filter { it.lat != 0.0 || it.lon != 0.0 }
                        .map { PuntoFermata(it.stopId, it.name, it.code, it.lat, it.lon) },
                    mezzi = mezzo?.let {
                        listOf(PuntoMezzo(it.vehicleId, it.lat, it.lon, it.bearing))
                    } ?: emptyList(),
                    tracciato = tracciato,
                    coloreLinea = linea?.color,
                    modifier = Modifier.fillMaxSize(),
                    fermataToccata = { apriFermata(it.stopId) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        when {
            !caricata -> Nota("Carico la corsa...", c)
            fermate.isEmpty() && errore -> Nota("La corsa non si lascia caricare.", c)
            fermate.isEmpty() -> Nota("Di questa corsa non si sa piu' niente. Forse e' gia' finita.", c)
            else -> LazyColumn(state = stato) {
                items(fermate, key = { it.stopId + "-" + it.sequenza }) { f ->
                    val indice = fermate.indexOf(f)
                    RigaFermataCorsa(
                        fermata = f,
                        adesso = adesso,
                        prossima = indice == prossima,
                        passata = prossima >= 0 && indice < prossima,
                        primo = indice == 0,
                        ultimo = indice == fermate.lastIndex,
                        c = c,
                        apri = { apriFermata(f.stopId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Intestazione(
    linea: Linea?,
    mezzo: MezzoCorsa?,
    destinazione: String?,
    adesso: Long,
    c: Palette,
    apriAvvisi: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "Mezzo localizzato" con l'eta' del dato, oppure "Mezzo
            // fantasma": sono due situazioni diverse e vanno nominate.
            val eta = mezzo?.ts?.let { minutiA(it, adesso)?.let { m -> -m } }
            Text(
                text = when {
                    mezzo == null -> "Mezzo fantasma"
                    eta == null -> "Mezzo localizzato"
                    eta <= 0 -> "Mezzo localizzato ora"
                    else -> "Mezzo visto $eta min fa"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (mezzo == null) c.neutral500 else c.live600,
                modifier = Modifier.weight(1f),
            )
            AzioniIntestazione(apriAvvisi)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = linea?.shortName ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = linea?.textColor?.let { colore(it) } ?: c.neutral100,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .width(56.dp)
                    .background(
                        linea?.color?.let { colore(it) } ?: c.neutral900,
                        RoundedCornerShape(4.dp),
                    )
                    .padding(vertical = 4.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = destinazione ?: "Corsa",
                style = stileNome,
                fontWeight = FontWeight.SemiBold,
                color = c.neutral900,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Una fermata della corsa.
 *
 * Tre stati visibili, e servono tutti: PASSATA in grigio spento, PROSSIMA col
 * pallino verde pieno e l'attesa in minuti, FUTURA con l'orario di passaggio.
 * Su una corsa di cinquanta fermate senza questa distinzione non si capisce
 * dove sia arrivato il mezzo.
 */
@Composable
private fun RigaFermataCorsa(
    fermata: FermataCorsa,
    adesso: Long,
    prossima: Boolean,
    passata: Boolean,
    primo: Boolean,
    ultimo: Boolean,
    c: Palette,
    apri: () -> Unit,
) {
    val minuti = minutiA(fermata.etaTs, adesso)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = apri)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(18.dp).height(44.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(if (primo) 0.dp else 21.dp)
                        .background(if (passata) c.neutral200 else c.neutral300),
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .width(2.dp)
                        .height(if (ultimo) 0.dp else 21.dp)
                        .background(c.neutral300),
                )
            }
            Box(
                Modifier
                    .size(if (prossima) 12.dp else 9.dp)
                    .background(
                        if (prossima) c.live500 else c.neutral50,
                        RoundedCornerShape(50),
                    )
                    .border(
                        2.dp,
                        when {
                            prossima -> c.live500
                            passata -> c.neutral300
                            else -> c.neutral400
                        },
                        RoundedCornerShape(50),
                    ),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = fermata.name,
            style = stileNome,
            color = if (passata) c.neutral400 else c.neutral900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(74.dp), contentAlignment = Alignment.CenterEnd) {
            when {
                fermata.etaTs == null -> Text(
                    "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral400,
                )
                // Sulla prossima si scrivono i MINUTI, non l'ora: e' l'unica
                // riga dove la domanda e' "quanto manca" e non "a che ora".
                prossima && minuti != null && minuti <= 0 -> Text(
                    "in arrivo",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.live600,
                )
                prossima && minuti != null -> Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$minuti",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.live600,
                    )
                    Text(
                        " min",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = c.live600,
                    )
                }
                else -> Text(
                    text = ora(fermata.etaTs),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (passata) c.neutral400 else c.neutral700,
                )
            }
        }
    }
}

private fun minutiA(iso: String?, adessoMs: Long): Int? {
    if (iso == null) return null
    return try {
        val diff = Instant.parse(iso).toEpochMilli() - adessoMs
        Math.floorDiv(diff, 60_000L).toInt()
    } catch (e: Exception) {
        null
    }
}

private fun ora(iso: String?): String {
    if (iso == null) return "--:--"
    return try {
        ORA_MINUTI.format(Instant.parse(iso))
    } catch (e: Exception) {
        "--:--"
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

@Composable
private fun Nota(testo: String, c: Palette) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodyMedium,
        color = c.neutral500,
        modifier = Modifier.padding(16.dp),
    )
}
