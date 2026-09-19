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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.rotate
import dev.disagio.busroma.dati.PassaggioLinea
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import dev.disagio.busroma.mappa.MappaPercorso
import dev.disagio.busroma.mappa.PuntoFermata
import dev.disagio.busroma.mappa.PuntoMezzo
import dev.disagio.busroma.ui.Croce
import dev.disagio.busroma.ui.PuntaGiu
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    apriFermata: (String) -> Unit,
    apriCorsa: (String) -> Unit,
    apriAvvisi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPalette.current

    var linea by remember(routeId) { mutableStateOf<Linea?>(null) }
    /** Quale fermata del percorso ha il pannello aperto. */
    var apertaId by remember(routeId) { mutableStateOf<String?>(null) }
    var versi by remember(routeId) { mutableStateOf<List<Verso>>(emptyList()) }
    var verso by remember(routeId) { mutableStateOf<Int?>(null) }
    var fermate by remember(routeId) { mutableStateOf<List<FermataLinea>>(emptyList()) }
    /** Il tracciato per la mappa: 700 punti, si legge al cambio di verso. */
    var tracciato by remember(routeId) { mutableStateOf<List<List<Double>>?>(null) }
    /** La fermata toccata sulla mappa, per la schedina sopra la mappa. */
    var toccata by remember(routeId) { mutableStateOf<PuntoFermata?>(null) }
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
        // Cambiando verso l'elenco e' un altro: un pannello aperto resterebbe
        // agganciato a una fermata che non c'e' piu'.
        apertaId = null
        val v = verso ?: return@LaunchedEffect
        toccata = null
        try {
            val r = Api.fermateLinea(routeId, v)
            fermate = r.stops
            tracciato = r.shape?.coordinates
        } catch (e: Exception) {
            fermate = emptyList()
            tracciato = null
        }
    }

    // Mezzi: in ciclo, solo a schermata in primo piano.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(routeId, verso) {
        val v = verso ?: return@LaunchedEffect
        // I MEZZI DEL VERSO PRECEDENTE NON RESTANO. Prima cambiando verso
        // restavano i pallini verdi del verso vecchio fino alla prima risposta
        // - fino a quindici secondi di mezzi attaccati alle fermate sbagliate.
        mezzi = emptyList()
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

        // LA MAPPA STA SOPRA L'ELENCO, come sul web: prima "dov'e' il mio
        // autobus", poi la sequenza delle fermate. Altezza fissa perche' sotto
        // c'e' una lista che scorre, e una mappa che scorre dentro una lista
        // che scorre e' un litigio fra due gesti.
        if (fermate.isNotEmpty()) {
            // Respiro sopra e sotto: incollata al selettore del verso e
            // all'elenco, la mappa sembrava un ritaglio finito li' per errore
            // invece di un blocco a se'.
            Spacer(Modifier.height(12.dp))
            // Margine laterale e angoli tondi come sulla mappa del
            // pianificatore: a tutta larghezza sembrava incollata ai bordi
            // dello schermo mentre tutto il resto della pagina respira di
            // sedici punti.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(240.dp)
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                MappaPercorso(
                    fermate = fermate.map {
                        PuntoFermata(it.stopId, it.name, it.code, it.lat, it.lon)
                    },
                    mezzi = mezzi.map {
                        PuntoMezzo(it.vehicleId, it.lat, it.lon, it.bearing)
                    },
                    tracciato = tracciato,
                    coloreLinea = linea?.color,
                    modifier = Modifier.fillMaxSize(),
                    fermataToccata = { toccata = it },
                )
                toccata?.let { f ->
                    SchedinaFermata(
                        fermata = f,
                        c = c,
                        chiudi = { toccata = null },
                        apri = { apriFermata(f.stopId) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // LE PARTENZE DALLA PRIMA FERMATA, come sul web.
        //
        // Sta QUI, a livello di linea, e non nel pannello di una fermata: la
        // domanda e' "quando parte la 71", e la risposta sono le partenze dal
        // capolinea. L'orario di una fermata intermedia e' un'altra cosa, e
        // metterlo al posto di questo l'ha resa irraggiungibile.
        if (fermate.isNotEmpty()) {
            SezionePartenze(routeId, verso, fermate.first(), c)
            Spacer(Modifier.height(4.dp))
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
                        aperta = apertaId == f.stopId,
                        c = c,
                        // UNA SOLA fermata aperta alla volta: con trenta righe
                        // e nessun limite, l'elenco del percorso diventava un
                        // muro di pannelli e non si scorreva piu'.
                        alterna = { apertaId = if (apertaId == f.stopId) null else f.stopId },
                    )
                    if (apertaId == f.stopId) {
                        PannelloFermata(
                            routeId = routeId,
                            fermata = f,
                            c = c,
                            apriFermata = apriFermata,
                            apriCorsa = apriCorsa,
                        )
                    }
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
 * Il verso, scelto da una tendina.
 *
 * PRIMA ERANO DUE TASTI AFFIANCATI, e il difetto era l'altezza: con un
 * capolinea lungo il testo andava a capo e quel tasto diventava piu' alto
 * dell'altro. Pareggiarli con IntrinsicSize risolveva l'allineamento ma
 * ingrandiva entrambi i riquadri, che e' il rimedio che all'utente non e'
 * piaciuto - e a ragione, perche' due tasti alti il doppio per una scelta fra
 * due cose e' peso visivo comprato a niente.
 *
 * Una riga sola a larghezza PIENA toglie il problema alla radice invece di
 * compensarlo: un capolinea romano su tutta la larghezza ci sta su una riga,
 * quindi non va a capo, e non c'e' nessun fratello con cui disallinearsi.
 *
 * Su cento linee dell'API, 26 hanno un verso e 74 ne hanno due: nessuna di
 * piu'. Con due sole voci una tendina costa un tocco in piu' di uno scambio
 * diretto, ma mostra sempre dove sei senza doverlo dedurre, ed e' il
 * comportamento che si aspetta chi vede una punta di freccia.
 */
@Composable
private fun SceltaVerso(versi: List<Verso>, attuale: Int, c: Palette, scegli: (Int) -> Unit) {
    var aperta by remember { mutableStateOf(false) }
    val scelto = versi.firstOrNull { it.directionId == attuale }

    // BoxWithConstraints per sapere quanto e' larga la riga: la tendina di
    // Compose si dimensiona sul suo contenuto, non sull'elemento che la apre,
    // e usciva stretta e sfalsata - "non sembra naturale", e non lo sembrava
    // perche' non lo e'. Passandole la larghezza misurata si allinea alla
    // riga come fa una tendina vera.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val largaQuanto = maxWidth
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(c.neutral50)
                .border(1.dp, c.neutral300, RoundedCornerShape(4.dp))
                .clickable { aperta = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "verso",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral500,
                )
                Text(
                    text = scelto?.headsign ?: "\u2014",
                    style = stileNome,
                    fontWeight = FontWeight.SemiBold,
                    color = c.neutral900,
                    // Due righe consentite e nessun troncamento: e' un elemento
                    // solo, quindi se cresce non disallinea niente.
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(8.dp))
            PuntaGiu(c.neutral500, Modifier.size(18.dp))
        }

        DropdownMenu(
            expanded = aperta,
            onDismissRequest = { aperta = false },
            modifier = Modifier.width(largaQuanto).background(c.neutral50),
        ) {
            versi.forEach { v ->
                val corrente = v.directionId == attuale
                DropdownMenuItem(
                    text = {
                        Text(
                            text = v.headsign ?: "\u2014",
                            style = stileNome,
                            fontWeight = if (corrente) FontWeight.SemiBold else FontWeight.Normal,
                            // Il verso in cui sei e' segnato col colore
                            // d'identita', non con una spunta: una spunta in
                            // un elenco di due voci e' rumore.
                            color = if (corrente) c.brand500 else c.neutral900,
                        )
                    },
                    onClick = {
                        aperta = false
                        if (!corrente) scegli(v.directionId)
                    },
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
    aperta: Boolean,
    c: Palette,
    alterna: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // LA RIGA INTERA APRE GLI ORARI, come sul web. Prima era inerte, e
            // arrivandoci da un arrivo senza mezzo tracciato si finiva su un
            // elenco di trenta nomi su cui non si poteva fare niente.
            .clickable(onClick = alterna)
            .padding(horizontal = 16.dp),
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
        Spacer(Modifier.width(6.dp))
        // La freccia ruota invece di cambiare glifo: dice "questa riga si apre"
        // anche da chiusa, che e' il motivo per cui prima nessuno provava a
        // toccarla.
        PuntaGiu(
            c.neutral400,
            Modifier
                .size(16.dp)
                .rotate(if (aperta) 180f else 0f),
        )
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

/**
 * Le partenze dal capolinea, con l'interruttore per tutto l'orario.
 *
 * E' la controparte della sezione "Partenze da ..." del web, e sta allo stesso
 * posto: subito sotto la mappa, prima dell'elenco delle fermate. Le prossime
 * otto come pastiglie di orario, verdi se il mezzo e' tracciato e grigie se
 * sono orario previsto — la stessa distinzione di tutta l'app.
 */
@Composable
private fun SezionePartenze(
    routeId: String,
    verso: Int?,
    prima: FermataLinea,
    c: Palette,
) {
    var partenze by remember(routeId, prima.stopId) {
        mutableStateOf<List<PassaggioLinea>?>(null)
    }
    var errore by remember(routeId, prima.stopId) { mutableStateOf(false) }
    var tuttoOrario by remember(routeId, prima.stopId) { mutableStateOf(false) }

    LaunchedEffect(routeId, prima.stopId) {
        try {
            partenze = Api.passaggiLineaAllaFermata(routeId, prima.stopId).arrivals
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errore = true
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Partenze da ${prima.name}",
                style = stileNome,
                fontWeight = FontWeight.SemiBold,
                color = c.neutral900,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            // L'interruttore compare solo col verso noto: il server ne ha
            // bisogno, e un tasto che non puo' funzionare non si mostra.
            if (verso != null) {
                Text(
                    text = if (tuttoOrario) "Solo le prossime" else "Tutto l'orario",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = c.brand600,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable { tuttoOrario = !tuttoOrario }
                        .padding(start = 10.dp, top = 14.dp, bottom = 14.dp),
                )
            }
        }

        if (tuttoOrario && verso != null) {
            OrarioCompleto(routeId = routeId, stopId = prima.stopId, verso = verso, c = c)
        } else {
            val p = partenze
            when {
                errore -> Nota2("Gli orari non si lasciano leggere.", c)
                p == null -> Nota2("Leggo gli orari...", c)
                p.isEmpty() -> Nota2("Nelle prossime 2 ore, niente.", c)
                else -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    p.take(8).forEach { d ->
                        Text(
                            text = oraDi(d.etaTs),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (d.isRealtime) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (d.isRealtime) c.live600 else c.neutral600,
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (d.isRealtime) c.live600 else c.neutral300,
                                    RoundedCornerShape(3.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** L'orario di partenza come lo legge un romano: "21:34", nel fuso di Roma. */
private fun oraDi(etaIso: String): String = try {
    ORA_MINUTI_LINEA.format(Instant.parse(etaIso))
} catch (e: Exception) {
    "--:--"
}

private val ORA_MINUTI_LINEA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Rome"))

@Composable
private fun Nota(testo: String, c: Palette) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodyMedium,
        color = c.neutral500,
        modifier = Modifier.padding(16.dp),
    )
}

/**
 * Il pannello che si apre sotto una fermata del percorso.
 *
 * Risponde a "quando passa QUESTA linea da QUI", che non e' la domanda della
 * pagina della fermata - quella risponde "cosa passa da qui", tutte le linee
 * insieme. Sono due schermate diverse perche' sono due domande diverse, e il
 * link in fondo porta dalla prima alla seconda.
 *
 * Gli orari si caricano ALL'APERTURA e non con l'elenco: una linea ha trenta
 * fermate, e chiedere i passaggi di tutte per mostrarne uno significherebbe
 * trenta richieste per niente.
 */
@Composable
private fun PannelloFermata(
    routeId: String,
    fermata: FermataLinea,
    c: Palette,
    apriFermata: (String) -> Unit,
    apriCorsa: (String) -> Unit,
) {
    var passaggi by remember(routeId, fermata.stopId) {
        mutableStateOf<List<PassaggioLinea>?>(null)
    }
    var errore by remember(routeId, fermata.stopId) { mutableStateOf(false) }
    LaunchedEffect(routeId, fermata.stopId) {
        try {
            passaggi = Api.passaggiLineaAllaFermata(routeId, fermata.stopId).arrivals
        } catch (e: Exception) {
            errore = true
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 44.dp, end = 16.dp, bottom = 10.dp),
    ) {
        Column(
            Modifier
                .drawBehind {
                    // Filetto che lega il pannello al pallino da cui esce.
                    drawRect(
                        color = c.neutral200,
                        size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height),
                    )
                }
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val p = passaggi
            when {
                errore -> Nota2("Gli orari non si lasciano leggere.", c)
                p == null -> Nota2("Leggo gli orari...", c)
                p.isEmpty() -> Nota2("Nelle prossime 2 ore, niente.", c)
                else -> p.take(3).forEach { a ->
                    RigaPassaggio(a, c) { a.tripId?.let(apriCorsa) }
                }
            }

            Text(
                text = buildString {
                    append("Tutte le linee di questa fermata")
                    fermata.code?.let { append(" (palina $it)") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.brand600,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clickable { apriFermata(fermata.stopId) }
                    .padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun RigaPassaggio(a: PassaggioLinea, c: Palette, apri: () -> Unit) {
    val orario = try {
        java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            .format(java.time.Instant.parse(a.etaTs).atZone(java.time.ZoneId.of("Europe/Rome")))
    } catch (e: Exception) {
        "--:--"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 30.dp)
            // Cliccabile solo se c'e' una corsa da aprire: qui, al contrario
            // della riga degli arrivi, non esiste una seconda destinazione -
            // la linea e' questa, ci siamo gia'.
            .then(if (a.tripId != null) Modifier.clickable(onClick = apri) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = orario,
            style = MaterialTheme.typography.bodyMedium,
            color = c.neutral900,
            modifier = Modifier.width(52.dp),
        )
        if (a.isRealtime) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(c.live500, RoundedCornerShape(50)),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = when {
                a.minutes == null -> ""
                a.minutes <= 0 -> "in arrivo"
                else -> "tra ${a.minutes} min"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (a.isRealtime) c.live600 else c.neutral500,
        )
    }
}

@Composable
private fun Nota2(testo: String, c: Palette) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodySmall,
        color = c.neutral500,
    )
}

/**
 * La scheda che compare toccando una fermata sulla mappa.
 *
 * Sul web e' un popup HTML dentro la tela della mappa, con un link da
 * centrare col mouse. Qui e' una scheda vera: usa i caratteri e i colori
 * dell'app, e il bersaglio per andare agli arrivi e' largo come un dito.
 */
@Composable
private fun SchedinaFermata(
    fermata: PuntoFermata,
    c: Palette,
    chiudi: () -> Unit,
    apri: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(c.neutral50)
            .border(1.dp, c.neutral300, RoundedCornerShape(6.dp))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = fermata.nome,
                style = stileNome,
                fontWeight = FontWeight.SemiBold,
                color = c.neutral900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            fermata.palina?.let {
                Text(
                    text = "palina $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral500,
                )
            }
        }
        Text(
            text = "Vedi arrivi",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = c.brand600,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = apri)
                .padding(horizontal = 10.dp, vertical = 12.dp),
        )
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = chiudi),
            contentAlignment = Alignment.Center,
        ) {
            Croce(c.neutral400, Modifier.size(14.dp))
        }
    }
}
