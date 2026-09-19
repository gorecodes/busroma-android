package dev.disagio.busroma.linea

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.VoceOrario
import dev.disagio.busroma.ui.theme.Palette
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Il fuso che conta per "oggi" e "domani": mai UTC, altrimenti dopo mezzanotte si chiede il giorno sbagliato. */
private val ZonaRoma: ZoneId = ZoneId.of("Europe/Rome")

private enum class GiornoOrario { OGGI, DOMANI }

/**
 * L'orario completo di una linea a una fermata: tutte le partenze del giorno,
 * non i prossimi novanta minuti.
 *
 * Sul web esiste come interruttore "Tutto l'orario / Solo le prossime" dentro
 * la pagina della linea, e legge le partenze dalla PRIMA fermata del verso.
 * Qui si fa meglio con lo stesso dato: il pannello di una fermata esiste già
 * per ogni fermata del percorso, quindi l'orario si chiede per LA fermata che
 * l'utente ha aperto — che è la domanda vera ("a che ora passa da QUI").
 *
 * Dentro, un interruttore oggi/domani: dopo mezzanotte "il primo bus" è una
 * domanda diversa da "l'ultimo", e il server accetta la data.
 */
@Composable
fun OrarioCompleto(
    routeId: String,
    stopId: String,
    verso: Int,
    c: Palette,
    modifier: Modifier = Modifier,
) {
    var giorno by remember(routeId, stopId, verso) { mutableStateOf(GiornoOrario.OGGI) }
    // Cambia solo per far ripartire l'effetto quando si tocca "riprova":
    // il valore in se' non serve a nient'altro.
    var tentativo by remember(routeId, stopId, verso) { mutableIntStateOf(0) }

    var voci by remember(routeId, stopId, verso, giorno) { mutableStateOf<List<VoceOrario>?>(null) }
    var errore by remember(routeId, stopId, verso, giorno) { mutableStateOf(false) }

    LaunchedEffect(routeId, stopId, verso, giorno, tentativo) {
        voci = null
        errore = false
        try {
            val data = LocalDate.now(ZonaRoma)
                .plusDays(if (giorno == GiornoOrario.DOMANI) 1 else 0)
                .toString()
            voci = Api.orarioCompleto(routeId, stopId, verso, data).timetable
        } catch (e: CancellationException) {
            // Non e' un errore di rete: e' il cambio di giorno o di fermata
            // che ha annullato la richiesta precedente, gia' inutile.
            throw e
        } catch (e: Exception) {
            errore = true
        }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SelettoreGiorno(giorno, c) { giorno = it }

        val v = voci
        when {
            errore -> RigaErrore("L'orario non si lascia leggere.", c) { tentativo++ }
            v == null -> Text(
                text = "Leggo l'orario...",
                style = MaterialTheme.typography.bodySmall,
                color = c.neutral500,
            )
            v.isEmpty() -> Text(
                // Capita nei giorni festivi, dove alcune linee non fanno servizio.
                text = "Nessuna corsa in questo giorno.",
                style = MaterialTheme.typography.bodySmall,
                color = c.neutral500,
            )
            else -> Griglia(v, giorno, c, modifier)
        }
    }
}

/**
 * Oggi/domani, come due schede. Resta visibile anche a vuoto o in errore:
 * e' l'unica azione che ha senso quando "questo giorno" non da' nulla — si
 * prova l'altro.
 */
@Composable
private fun SelettoreGiorno(giorno: GiornoOrario, c: Palette, scegli: (GiornoOrario) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Scheda("Oggi", giorno == GiornoOrario.OGGI, c) { scegli(GiornoOrario.OGGI) }
        Scheda("Domani", giorno == GiornoOrario.DOMANI, c) { scegli(GiornoOrario.DOMANI) }
    }
}

@Composable
private fun Scheda(testo: String, selezionata: Boolean, c: Palette, tocco: () -> Unit) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (selezionata) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selezionata) c.brand600 else c.neutral500,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selezionata) c.brand50 else c.neutral100)
            .clickable(onClick = tocco)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    )
}

@Composable
private fun RigaErrore(testo: String, c: Palette, riprova: () -> Unit) {
    Column {
        Text(testo, style = MaterialTheme.typography.bodySmall, color = c.neutral500)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Riprova",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = c.brand600,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClick = riprova)
                .padding(vertical = 11.dp),
        )
    }
}

/**
 * L'orario raggruppato PER ORA, come quello di carta appeso alla palina: una
 * riga per ora, i minuti di fila accanto. Un elenco verticale di duecento
 * righe sarebbe illeggibile; questa forma si scorre con l'occhio e si trova
 * subito la fascia che interessa.
 */
@Composable
private fun Griglia(
    voci: List<VoceOrario>,
    giorno: GiornoOrario,
    c: Palette,
    modifier: Modifier = Modifier,
) {
    // L'ora si legge una volta sola qui, al disegno: un orario di carta non
    // si muove da solo, e "adesso" non ha senso per la scheda di domani.
    val prossimoIndice = remember(voci, giorno) {
        if (giorno != GiornoOrario.OGGI) {
            null
        } else {
            val adesso = LocalTime.now(ZonaRoma).toSecondOfDay()
            voci.indexOfFirst { it.departureS >= adesso }.takeIf { it >= 0 }
        }
    }

    val perOra = remember(voci) {
        // SI RAGGRUPPA SUI SECONDI, non sul testo "hh". L'orario scavalca la
        // mezzanotte — verificato: l'ultima corsa della 60 ha
        // departure_s 86400, cioe' le 00:00 del giorno dopo — e una linea con
        // servizio oltre le ventiquattro ore avrebbe due fasce diverse nella
        // stessa chiave "00", mescolate in una riga sola. Sui secondi le due
        // fasce restano separate e nell'ordine giusto per costruzione, non per
        // fortuna dell'ordine dei dati.
        val m = LinkedHashMap<Int, MutableList<Int>>()
        voci.forEachIndexed { indice, voce ->
            m.getOrPut(voce.departureS / 3600) { mutableListOf() }.add(indice)
        }
        m
    }

    // SI SCORRE DA SE', E DENTRO UN'ALTEZZA MASSIMA.
    //
    // Correzione di un difetto visto in uso: l'orario sta nel blocco FISSO
    // della pagina della linea - intestazione, verso, mappa, partenze - mentre
    // l'unica cosa che scorre e' la lista delle fermate sotto. Venti righe di
    // orario crescevano quindi fuori dallo schermo senza che nulla le potesse
    // scorrere.
    //
    // Si sceglie una LazyColumn con un tetto di altezza invece di rendere
    // scorrevole tutta la pagina: la mappa resta dove e', e una mappa che
    // scorre dentro una lista che scorre e' un litigio fra due gesti (e' la
    // ragione per cui questa pagina e' fatta cosi'). Il tetto lascia in vista
    // il titolo e l'interruttore mentre si scorre l'orario.
    val statoLista = rememberLazyListState()
    val righe = remember(perOra) { perOra.toList() }

    // Si parte dall'ora della prossima partenza, non dalle cinque del mattino:
    // alle nove di sera aprire l'orario e vedere "05 30 46" e' inutile.
    LaunchedEffect(righe, prossimoIndice) {
        val i = prossimoIndice ?: return@LaunchedEffect
        val riga = righe.indexOfFirst { (_, indici) -> i in indici }
        if (riga > 0) statoLista.scrollToItem(riga)
    }

    LazyColumn(
        state = statoLista,
        // Nessun tetto d'altezza: questa lista vive in una schermata sua e
        // prende lo spazio che c'e'. Il tetto serviva quando stava dentro la
        // pagina della linea, dove spremeva l'elenco delle fermate.
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(righe, key = { (ora, _) -> ora }) { (ora, indici) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    // % 24: l'ora 24 e' mezzanotte, la 25 e' l'una.
                    text = (ora % 24).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.neutral700,
                    modifier = Modifier.width(28.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = buildAnnotatedString {
                        indici.forEachIndexed { pos, indice ->
                            val passata = prossimoIndice != null && indice < prossimoIndice
                            val prossima = indice == prossimoIndice
                            withStyle(
                                SpanStyle(
                                    color = when {
                                        prossima -> c.brand600
                                        passata -> c.neutral400
                                        else -> c.neutral900
                                    },
                                    fontWeight = if (prossima) FontWeight.Bold else FontWeight.Normal,
                                ),
                            ) {
                                append(voci[indice].hhmm.takeLast(2))
                            }
                            if (pos != indici.lastIndex) append(" ")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
