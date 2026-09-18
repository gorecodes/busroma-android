package dev.disagio.busroma.linea

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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.FermataLinea
import dev.disagio.busroma.dati.Linea
import dev.disagio.busroma.dati.Mezzo
import dev.disagio.busroma.dati.Verso
import dev.disagio.busroma.dati.etichettaTipo
import dev.disagio.busroma.dati.nomeLinea
import dev.disagio.busroma.ui.AzioniIntestazione
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sqrt

/** I mezzi si muovono: quindici secondi, come sulla pagina di una fermata. */
private const val INTERVALLO_MS = 15_000L

/**
 * Oltre questa distanza non si considera il mezzo "a quella fermata".
 * 500 metri come sul web: a Roma le fermate distano spesso 300-400 metri, e
 * senza un limite un mezzo in mezzo al nulla verrebbe attaccato alla fermata
 * piu' vicina comunque, dando un'informazione falsa.
 */
private const val VICINANZA_M = 500.0

/**
 * La pagina di una linea: versi, fermate in ordine, e dove sono i mezzi.
 *
 * LA MAPPA NON C'E' ANCORA, ed e' una scelta di sequenza: e' la dipendenza piu'
 * pesante del progetto e serve a tre schermate, quindi ha un passo suo. Ma
 * "dove sono i mezzi" si risponde anche senza mappa, ed e' l'informazione che
 * conta di piu': il pallino verde sulla riga di una fermata dice che il bus e'
 * li' adesso.
 */
@Composable
fun SchermataLinea(
    routeId: String,
    versoIniziale: Int?,
    apriAvvisi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPalette.current

    var linea by remember(routeId) { mutableStateOf<Linea?>(null) }
    var versi by remember(routeId) { mutableStateOf<List<Verso>>(emptyList()) }
    var verso by remember(routeId) { mutableStateOf<Int?>(null) }
    var fermate by remember(routeId) { mutableStateOf<List<FermataLinea>>(emptyList()) }
    var mezzi by remember(routeId) { mutableStateOf<List<Mezzo>>(emptyList()) }
    var errore by remember(routeId) { mutableStateOf(false) }

    // Anagrafica e versi: una volta sola.
    LaunchedEffect(routeId) {
        try {
            val r = Api.linea(routeId)
            linea = r.route
            versi = r.directions
            // Il verso passato dalla ricerca se e' valido, altrimenti il primo.
            verso = versoIniziale?.takeIf { v -> r.directions.any { it.directionId == v } }
                ?: r.directions.firstOrNull()?.directionId
        } catch (e: Exception) {
            errore = true
        }
    }

    // Fermate: al cambio di verso.
    LaunchedEffect(routeId, verso) {
        val v = verso ?: return@LaunchedEffect
        fermate = try {
            Api.fermateLinea(routeId, v).stops
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Mezzi: in ciclo, solo a schermata in primo piano.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(routeId, verso) {
        val v = verso ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                mezzi = try {
                    Api.mezziLinea(routeId, v).vehicles
                } catch (e: Exception) {
                    // Si tengono i precedenti: un buco di rete non deve far
                    // sparire i mezzi dalla lista come se fossero scomparsi.
                    mezzi
                }
                delay(INTERVALLO_MS)
            }
        }
    }

    val perFermata = remember(mezzi, fermate) { mezziPerFermata(mezzi, fermate) }

    Column(modifier.fillMaxSize().background(c.neutral100)) {
        Intestazione(linea, mezzi.size, c, apriAvvisi)

        if (versi.size > 1 && verso != null) {
            SceltaVerso(versi, verso!!, c) { verso = it }
        }

        when {
            errore -> Nota("La linea non si lascia caricare.", c)
            fermate.isEmpty() -> Nota("Carico il percorso...", c)
            else -> LazyColumn {
                items(fermate, key = { it.stopId + "-" + it.sequenza }) { f ->
                    RigaFermataLinea(
                        fermata = f,
                        mezziQui = perFermata[f.stopId] ?: 0,
                        primo = f.sequenza == fermate.first().sequenza,
                        ultimo = f.sequenza == fermate.last().sequenza,
                        c = c,
                    )
                }
            }
        }
    }
}

@Composable
private fun Intestazione(
    linea: Linea?,
    quantiMezzi: Int,
    c: Palette,
    apriAvvisi: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = linea?.shortName ?: "",
            style = MaterialTheme.typography.headlineMedium,
            color = linea?.textColor?.let { colore(it) } ?: c.neutral100,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .width(70.dp)
                .background(
                    linea?.color?.let { colore(it) } ?: c.neutral900,
                    RoundedCornerShape(4.dp),
                )
                .padding(vertical = 5.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = linea?.let { nomeLinea(it.longName, it.type) } ?: "Linea",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.neutral900,
                maxLines = 2,
            )
            // "3 mezzi in linea" e' il dato che dice se la linea e' viva
            // adesso: un percorso senza mezzi tracciati e' un orario, non un
            // servizio in corso.
            Text(
                text = if (linea == null) "" else {
                    // Il tipo si scrive SOLO se il titolo non e' gia' il tipo:
                    // 360 linee su 434 hanno long_name vuoto, quindi il titolo
                    // diventa "Bus" e ripeterlo sotto dava "Bus / Bus · 2
                    // mezzi in linea". E' la stessa guardia del web.
                    val titoloGiaTipo = linea.longName?.trim().isNullOrEmpty()
                    val prefisso = if (titoloGiaTipo) "" else etichettaTipo(linea.type) + " · "
                    prefisso + when (quantiMezzi) {
                        0 -> "nessun mezzo tracciato"
                        1 -> "1 mezzo in linea"
                        else -> "$quantiMezzi mezzi in linea"
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (quantiMezzi > 0) c.live600 else c.neutral500,
            )
        }
        AzioniIntestazione(apriAvvisi)
    }
}

/**
 * I due versi come due tasti larghi, non come un menu: sono sempre due, e i
 * capolinea romani sono lunghi - su una riga sola si troncherebbero sempre, ed
 * e' il difetto che sul web e' stato corretto mandandoli a capo.
 *
 * I DUE TASTI DEVONO ESSERE ALTI UGUALE. Andando a capo, un capolinea lungo
 * rendeva il suo tasto piu' alto dell'altro: sulla 982 il verso DICIASSETTESIMA
 * OLIMPIADE occupava due righe e STAZIONE QUATTRO VENTI una, e i due riquadri
 * non si allineavano in basso. Sul web non succede perche' e' una griglia CSS,
 * che pareggia le celle da se'. Qui si ottiene con height(IntrinsicSize.Max)
 * sulla riga - che misura il piu' alto dei due - e fillMaxHeight sui figli,
 * che li fa arrivare entrambi a quell'altezza.
 */
@Composable
private fun SceltaVerso(versi: List<Verso>, attuale: Int, c: Palette, scegli: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        versi.forEach { v ->
            val scelto = v.directionId == attuale
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (scelto) c.neutral900 else c.neutral50)
                    .border(
                        1.dp,
                        if (scelto) c.neutral900 else c.neutral300,
                        RoundedCornerShape(4.dp),
                    )
                    .clickable { scegli(v.directionId) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "verso",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (scelto) c.neutral400 else c.neutral500,
                )
                Text(
                    text = v.headsign ?: "—",
                    style = stileNome,
                    fontWeight = FontWeight.SemiBold,
                    color = if (scelto) c.neutral50 else c.neutral900,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * Una fermata del percorso, con la linea del tracciato a sinistra.
 *
 * Il pallino diventa verde e pieno quando un mezzo e' li' adesso: e' la
 * risposta a "dov'e' il mio autobus" senza bisogno di una mappa.
 */
@Composable
private fun RigaFermataLinea(
    fermata: FermataLinea,
    mezziQui: Int,
    primo: Boolean,
    ultimo: Boolean,
    c: Palette,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Il filo del percorso: si interrompe al primo e all'ultimo capolinea,
        // altrimenti sembra che la linea continui oltre.
        Box(Modifier.width(18.dp).height(46.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                // Senza questo il filo resta a sinistra e i pallini al centro:
                // il tracciato sembrava sfalsato di una decina di pixel.
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(if (primo) 0.dp else 22.dp)
                        .background(c.neutral300),
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .width(2.dp)
                        .height(if (ultimo) 0.dp else 22.dp)
                        .background(c.neutral300),
                )
            }
            Box(
                Modifier
                    .size(if (mezziQui > 0) 12.dp else 9.dp)
                    .background(
                        if (mezziQui > 0) c.live500 else c.neutral50,
                        RoundedCornerShape(50),
                    )
                    .border(
                        2.dp,
                        if (mezziQui > 0) c.live500 else c.neutral400,
                        RoundedCornerShape(50),
                    ),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = fermata.name,
            style = stileNome,
            color = c.neutral900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (mezziQui > 0) {
            Text(
                text = if (mezziQui == 1) "qui" else "qui ×$mezziQui",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = c.live600,
            )
        }
    }
}

/**
 * A quale fermata sta ogni mezzo, dalle sue coordinate.
 *
 * NON si usa `next_stop_id` del feed, che e' spesso nullo anche con il mezzo
 * localizzato: e' la stessa scelta del web, dove `liveByStop` cerca la fermata
 * piu' vicina al GPS. Cosi' il dato dipende solo dalla posizione, che ATAC
 * manda sempre.
 *
 * La distanza e' equirettangolare e non geodetica: su qualche centinaio di
 * metri alla latitudine di Roma l'errore e' di centimetri, e qui serve solo a
 * scegliere la fermata piu' vicina fra due che distano centinaia di metri.
 */
private fun mezziPerFermata(mezzi: List<Mezzo>, fermate: List<FermataLinea>): Map<String, Int> {
    if (fermate.isEmpty() || mezzi.isEmpty()) return emptyMap()
    val conteggio = mutableMapOf<String, Int>()
    for (m in mezzi) {
        var migliore: String? = null
        var distanzaMigliore = Double.MAX_VALUE
        for (f in fermate) {
            val d = distanzaM(m.lat, m.lon, f.lat, f.lon)
            if (d < distanzaMigliore) {
                distanzaMigliore = d
                migliore = f.stopId
            }
        }
        if (migliore != null && distanzaMigliore < VICINANZA_M) {
            conteggio[migliore] = (conteggio[migliore] ?: 0) + 1
        }
    }
    return conteggio
}

private fun distanzaM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val r = 6_371_000.0
    val rad = Math.PI / 180
    val x = (bLon - aLon) * rad * cos((aLat + bLat) / 2 * rad)
    val y = (bLat - aLat) * rad
    return sqrt(x * x + y * y) * r
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
