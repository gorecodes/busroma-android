package dev.disagio.busroma.ricerca

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.disagio.busroma.ui.battito
import dev.disagio.busroma.dati.ArrivoVicino
import dev.disagio.busroma.arrivi.minutiDa
import dev.disagio.busroma.posizione.Posizione
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome

/**
 * Entrambi i permessi insieme: e' l'unico modo in cui Android mostra la scelta
 * fra "Precisa" e "Approssimata". Chiedendo solo quella fine il sistema non
 * offrirebbe l'alternativa; chiedendo solo l'approssimata la precisa non si
 * potrebbe piu' ottenere.
 */
private val PERMESSI = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** Oltre, la schermata iniziale diventa una lista infinita. */
private const val QUANTI = 8

/**
 * Come ordinare. Distanza per prima, come sul web: in strada la domanda e'
 * "quale fermata raggiungo", e solo dopo "quanto aspetto".
 */
private enum class Ordine(val etichetta: String) {
    Distanza("Distanza"),
    Attesa("Attesa"),
}

@Composable
fun SezioneVicine(apri: (String) -> Unit, c: Palette) {
    val contesto = LocalContext.current
    val vm: VicineViewModel = viewModel()
    val stato by vm.stato.collectAsStateWithLifecycle()
    var ordine by remember { mutableStateOf(Ordine.Distanza) }

    // Se il permesso c'e' gia', si carica da se': il tasto serviva a chiederlo.
    LaunchedEffect(Unit) { vm.caricaSePossibile(contesto) }

    // E POI SI TIENE AGGIORNATO. Solo a schermata in primo piano: fuori da qui
    // la coroutine viene annullata e non si interroga niente dalla tasca.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.ciclo(contesto) }
    }

    // L'orologio che batte: l'attesa scende da se' fra una risposta e l'altra,
    // invece di restare ferma sull'ultimo valore ricevuto.
    val ora = battito()

    fun carica() = vm.carica(contesto)

    val richiesta = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { esiti ->
        // Concesso o negato, si prova comunque: il ViewModel distingue i due
        // casi e mette il messaggio giusto. Due rami identici sarebbero solo
        // codice in piu'.
        carica()
    }

    fun chiediOCarica() {
        if (Posizione.permessoConcesso(contesto)) carica() else richiesta.launch(PERMESSI)
    }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Qui intorno",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = c.brand500,
                modifier = Modifier.weight(1f),
            )
            if (stato is StatoVicine.Trovati) {
                Text(
                    text = "Aggiorna",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = c.brand600,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { carica() }
                        .padding(horizontal = 10.dp, vertical = 13.dp),
                )
            }
        }

        when (val s = stato) {
            StatoVicine.Riposo -> Tasto("Usa la mia posizione", c, ::chiediOCarica)

            StatoVicine.InCorso -> Nota("Cerco dove sei...", c)

            is StatoVicine.Errore -> Column {
                Nota(s.messaggio, c)
                Tasto("Riprova", c, ::chiediOCarica)
            }

            is StatoVicine.Trovati -> if (s.arrivi.isEmpty()) {
                Nota("Nessun passaggio nei prossimi minuti qui intorno.", c)
            } else {
                Column {
                    avviso(s)?.let { testo ->
                        Text(
                            text = testo,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.warn700,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    SceltaOrdine(ordine, c) { ordine = it }

                    // Il secondo criterio a pareggio non e' un dettaglio: a
                    // parita' di distanza si vuole il bus che arriva prima, e a
                    // parita' di attesa quello piu' vicino. E' l'ordinamento
                    // del web.
                    val ordinati = when (ordine) {
                        Ordine.Distanza -> s.arrivi.sortedWith(
                            compareBy({ it.distanzaM ?: Int.MAX_VALUE }, { minuti(it, ora) }),
                        )
                        Ordine.Attesa -> s.arrivi.sortedWith(
                            compareBy({ minuti(it, ora) }, { it.distanzaM ?: Int.MAX_VALUE }),
                        )
                    }

                    ordinati.take(QUANTI).forEach { a ->
                        RigaArrivoVicino(a, ora, c) { apri(a.stopId) }
                        HorizontalDivider(color = c.neutral200)
                    }
                }
            }
        }
    }
}

private fun minuti(a: ArrivoVicino, adesso: Long) = minutiDa(a.etaTs, adesso) ?: Int.MAX_VALUE

fun avviso(s: StatoVicine.Trovati): String? {
    val incerta = s.incertezzaM != null && s.incertezzaM > Posizione.INCERTEZZA_ACCETTABILE_M
    val vecchia = s.etaMin >= 5
    return when {
        incerta && vecchia ->
            "Posizione di ${s.etaMin} min fa e approssimata di circa ${s.incertezzaM!!.toInt()} m."
        incerta ->
            "Posizione approssimata di circa ${s.incertezzaM!!.toInt()} m: " +
                "l'ordine può non essere esatto."
        vecchia ->
            "Posizione di ${s.etaMin} min fa: se ti sei spostato, aggiorna."
        else -> null
    }
}

/**
 * I due ordini, come due parole e non come un menu: sono due, e un menu per
 * due voci nasconde la scelta dietro un tocco in piu'.
 */
@Composable
private fun SceltaOrdine(attuale: Ordine, c: Palette, scegli: (Ordine) -> Unit) {
    Row(
        Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Ordina per",
            style = MaterialTheme.typography.bodySmall,
            color = c.neutral500,
        )
        Ordine.entries.forEach { o ->
            val scelto = o == attuale
            Text(
                text = o.etichetta,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (scelto) FontWeight.SemiBold else FontWeight.Normal,
                // Il chip scelto si riempie col colore d'identita' invece che
                // d'inchiostro, per la stessa ragione della barra in basso:
                // e' un controllo, non un dato.
                //
                // Il testo sopra e' lo SFONDO PAGINA e non neutral50: in scuro
                // il 50 e' la superficie in rilievo, quasi chiara, e sul
                // terracotta stava a 4,3:1. Lo sfondo pagina e' il piu' scuro
                // dei due in scuro e il piu' chiaro in chiaro, quindi va bene
                // in entrambi: 5,0:1 e 8,4:1.
                color = if (scelto) c.neutral100 else c.neutral600,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (scelto) c.brand500 else Color.Transparent)
                    .border(
                        1.dp,
                        if (scelto) c.brand500 else c.neutral300,
                        RoundedCornerShape(3.dp),
                    )
                    .clickable { scegli(o) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

/**
 * Una riga: linea, destinazione, fermata con distanza, attesa.
 *
 * Due informazioni in piu' rispetto alla schermata di una fermata - quale
 * palina e quanto e' lontana - perche' qui l'utente non ha ancora scelto dove
 * andare, e sono i due dati che gliela fanno scegliere.
 */
@Composable
private fun RigaArrivoVicino(a: ArrivoVicino, adesso: Long, c: Palette, apri: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = apri)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = a.shortName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = a.textColor?.let { colore(it) } ?: c.neutral100,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .width(44.dp)
                .background(a.color?.let { colore(it) } ?: c.neutral900, RoundedCornerShape(3.dp))
                .padding(vertical = 4.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = a.headsign ?: "Destinazione non indicata",
                style = stileNome,
                color = c.neutral900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = a.stopName + (a.distanzaM?.let { " · $it m" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = c.neutral500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        AttesaBreve(a, adesso, c)
    }
}

@Composable
private fun AttesaBreve(a: ArrivoVicino, adesso: Long, c: Palette) {
    val m = minutiDa(a.etaTs, adesso)
    val colore = if (a.isRealtime) c.live600 else c.neutral700
    Row(
        Modifier.width(66.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (a.isRealtime) {
            Box(Modifier.size(6.dp).background(c.live500, RoundedCornerShape(50)))
            Spacer(Modifier.width(4.dp))
        }
        when {
            m == null -> Text("—", style = MaterialTheme.typography.bodySmall, color = c.neutral400)
            m <= 0 -> Text(
                "ora",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colore,
            )
            else -> Row(verticalAlignment = Alignment.Bottom) {
                Text("$m", style = MaterialTheme.typography.titleLarge, color = colore)
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
private fun Tasto(etichetta: String, c: Palette, azione: () -> Unit) {
    Text(
        text = etichetta,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = c.neutral900,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(c.neutral50)
            .clickable(onClick = azione)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun Nota(testo: String, c: Palette) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodyMedium,
        color = c.neutral500,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
