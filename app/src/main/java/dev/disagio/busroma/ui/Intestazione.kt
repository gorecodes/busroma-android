package dev.disagio.busroma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.impostazioni.SceltaTema
import dev.disagio.busroma.impostazioni.Tema
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * L'angolo in alto a destra di ogni pagina: avvisi, stato del feed, tema.
 *
 * Sempre gli stessi tre, sempre in quest'ordine. Sul web e' `HeaderActions`, e
 * la ragione per cui esiste e' che quei tre elementi non appartengono a nessuna
 * schermata in particolare: sono lo stato dell'app.
 *
 * I primi due compaiono solo quando hanno qualcosa da dire - zero avvisi e
 * feed fresco non occupano spazio - quindi di solito la riga e' solo il tasto
 * del tema.
 */
@Composable
fun AzioniIntestazione(
    apriAvvisi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BadgeAvvisi(apriAvvisi)
        Spacer(Modifier.width(8.dp))
        StatoFeed()
        Spacer(Modifier.width(4.dp))
        TastoTema()
    }
}

/**
 * Intestazione di pagina: titolo a sinistra, azioni a destra.
 *
 * Il titolo dice cosa c'e' in pagina, non il nome dell'app: sul web la home
 * era l'unica delle quattro a scrivere "Bus Roma" al posto del proprio
 * contenuto, ed e' stato corretto.
 */
@Composable
fun Intestazione(
    titolo: String,
    apriAvvisi: () -> Unit,
    modifier: Modifier = Modifier,
    sottotitolo: String? = null,
) {
    val c = LocalPalette.current
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                text = titolo,
                style = MaterialTheme.typography.headlineMedium,
                color = c.neutral900,
            )
            sottotitolo?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.neutral500,
                )
            }
        }
        AzioniIntestazione(apriAvvisi)
    }
}

/**
 * Il conteggio degli avvisi di oggi.
 *
 * FORMA MINIMA per una ragione misurata sul web: l'intestazione di una pagina
 * su schermo stretto ha gia' indietro, contatore mezzi, stato del feed e tasto
 * tema. Scrivere "3 avvisi" sfondava la riga; triangolo piu' numero stanno in
 * trenta pixel, e il numero basta - chi vuole sapere cosa, tocca.
 *
 * Conta solo gli urgenti. I cantieri sono attivi sempre: metterli qui vorrebbe
 * dire avere il badge acceso per sempre, che e' il modo piu' efficace di
 * rendere invisibile un avviso.
 */
@Composable
private fun BadgeAvvisi(apri: () -> Unit) {
    val c = LocalPalette.current
    var urgenti by remember { mutableIntStateOf(0) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    urgenti = Api.avvisi().urgenti
                } catch (e: Exception) {
                    // L'intestazione non deve rompersi per un avviso.
                }
                delay(5 * 60_000L)
            }
        }
    }

    if (urgenti == 0) return

    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = apri)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Triangolo(c.warn600, Modifier.size(13.dp))
        Text(
            text = "$urgenti",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = c.warn600,
        )
    }
}

/**
 * Quando ATAC ha aggiornato l'ultima volta.
 *
 * NON e' "quando il browser ha ricaricato": e' l'orario in cui il worker ha
 * ricevuto dati freschi. La differenza conta, e sul web e' stata una
 * correzione: se il worker si ferma o il feed ATAC si blocca, l'app continua a
 * ricaricare felicemente dati vecchi senza che nessuno se ne accorga.
 *
 * Sotto i 90 secondi non compare: la presenza della scritta e' essa stessa un
 * segnale.
 */
@Composable
private fun StatoFeed() {
    val c = LocalPalette.current
    var stantio by remember { mutableStateOf<Int?>(null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    stantio = Api.statoFeed().stantioS
                } catch (e: Exception) {
                    // silenzioso
                }
                delay(30_000L)
            }
        }
    }

    val s = stantio ?: return
    if (s < 90) return

    val tinta = if (s < 300) c.warn600 else c.brand600
    val pallino = if (s < 300) c.warn500 else c.brand500
    val etichetta = if (s < 60) "${s}s fa" else "${Math.round(s / 60.0)} min fa"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(pallino, RoundedCornerShape(50)))
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (s < 300) etichetta else "dati fermi da $etichetta",
            style = MaterialTheme.typography.bodySmall,
            color = tinta,
        )
    }
}

/**
 * Chiaro/scuro, con la scelta che persiste.
 *
 * L'icona mostra la modalita' in cui si ANDREBBE: in chiaro una luna, perche'
 * premendo si passa a scuro. E' la stessa convenzione del web.
 */
@Composable
private fun TastoTema() {
    val c = LocalPalette.current
    val contesto = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .clickable {
                scope.launch {
                    // Si scrive l'opposto di quello che si vede ADESSO, non il
                    // giro Sistema->Chiaro->Scuro: un tasto che a volte non
                    // cambia niente di visibile e' un tasto rotto.
                    Tema.imposta(
                        contesto,
                        if (c.scura) SceltaTema.Chiaro else SceltaTema.Scuro,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (c.scura) {
            Sole(c.neutral500, Modifier.size(20.dp))
        } else {
            Luna(c.neutral500, Modifier.size(20.dp))
        }
    }
}
