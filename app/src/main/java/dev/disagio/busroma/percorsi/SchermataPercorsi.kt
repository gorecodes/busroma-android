package dev.disagio.busroma.percorsi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.disagio.busroma.dati.OpzioneItinerario
import dev.disagio.busroma.ui.AzioniIntestazione
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette

/**
 * Il pianificatore.
 *
 * DUE VISTE IN UNA DESTINAZIONE: il modulo con l'elenco delle proposte, e il
 * dettaglio di una proposta. Non sono due rotte perché sul web non lo sono —
 * il dettaglio è `/plan?sel=2`, quindi la voce "Percorsi" della barra resta
 * accesa anche mentre lo si guarda. Qui l'equivalente è un indice nello stato
 * e un BackHandler: l'indietro di sistema chiude il dettaglio invece di
 * uscire dalla sezione.
 */
@Composable
fun SchermataPercorsi(
    apriFermata: (String) -> Unit,
    apriAvvisi: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PercorsiViewModel = viewModel(),
) {
    val c = LocalPalette.current
    val contesto = LocalContext.current
    val stato by vm.stato.collectAsStateWithLifecycle()

    val aperta = stato.piano?.options?.getOrNull(stato.aperto ?: -1)

    BackHandler(enabled = aperta != null) { vm.apri(null) }

    Column(modifier.fillMaxSize().background(c.neutral100)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Percorsi",
                style = MaterialTheme.typography.headlineMedium,
                color = c.neutral900,
            )
            Spacer(Modifier.width(8.dp))
            // DICHIARATO, non nascosto: il calcolo non usa il tempo reale, gli
            // indirizzi vengono da OpenStreetMap e possono mancare. Meglio
            // scriverlo che far scoprire i limiti a chi sta correndo.
            Text(
                text = "BETA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = c.neutral100,
                modifier = Modifier
                    .background(c.brand500, RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            AzioniIntestazione(apriAvvisi)
        }

        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (aperta != null) {
                DettaglioItinerario(
                    opzione = aperta,
                    aPiedi = stato.piano?.walkOption,
                    indietro = { vm.apri(null) },
                    apriFermata = apriFermata,
                )
                return@Column
            }

            Text(
                text = "Da via a via, da fermata a fermata, o dalla tua posizione. " +
                    "Gli orari sono da tabella, senza il tempo reale: verifica sempre " +
                    "il passaggio sulla pagina della fermata.",
                style = MaterialTheme.typography.bodySmall,
                color = c.neutral600,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp),
            )

            Column(Modifier.padding(horizontal = 16.dp)) {
                CampoCapo(
                    etichetta = "Da",
                    valore = stato.da,
                    cambia = vm::impostaDa,
                    conPosizione = true,
                    statoGps = stato.gps,
                    chiediPosizione = { vm.usaLaMiaPosizione(contesto) },
                )
                CampoCapo(
                    etichetta = "A",
                    valore = stato.a,
                    cambia = vm::impostaA,
                    conPosizione = false,
                    statoGps = StatoGps.Fermo,
                    chiediPosizione = {},
                )
                QuandoParti(stato.quando, vm::impostaQuando, c)

                Text(
                    text = if (stato.calcolando) "Calcolo…" else "Cerca il percorso",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (stato.puoCercare) c.neutral50 else c.neutral500,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (stato.puoCercare) c.neutral900 else c.neutral300)
                        .clickable(enabled = stato.puoCercare) { vm.cerca() }
                        .padding(vertical = 12.dp),
                )

                when (stato.errore) {
                    ErrorePiano.Vuoto -> Text(
                        text = "Da qui non ci arrivi. Di notte Roma si restringe, oppure uno " +
                            "dei due capi è lontano da qualsiasi fermata.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.neutral600,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                    ErrorePiano.Guasto -> Text(
                        text = "Il calcolo si è arreso. Riprova",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.brand600,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable { vm.cerca() }
                            .padding(vertical = 20.dp),
                    )
                    null -> Unit
                }
            }

            val opzioni = stato.piano?.options ?: emptyList()
            if (opzioni.isNotEmpty()) {
                // Le proposte si mostrano TUTTE con i loro numeri e sceglie chi
                // legge: non esiste un itinerario giusto in assoluto, perché chi
                // ha fretta, chi non vuole cambiare e chi non vuole camminare ne
                // vogliono tre diversi.
                Text(
                    text = if (opzioni.size == 1) "Un percorso" else "${opzioni.size} percorsi",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = c.brand500,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                )
                HorizontalDivider(color = c.neutral300)
                opzioni.forEachIndexed { i, o ->
                    if (i > 0) HorizontalDivider(color = c.neutral200)
                    RigaOpzione(o, c) { vm.apri(i) }
                }
                HorizontalDivider(color = c.neutral300)

                stato.piano?.walkOption?.let {
                    val km = "%.1f".format(it.meters / 1000.0)
                    Text(
                        text = "Oppure tutto a piedi in ${it.minutes} min ($km km).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.neutral600,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RigaOpzione(o: OpzioneItinerario, c: Palette, apri: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = apri)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text(
                    text = "${o.durationMin} min",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = c.neutral900,
                )
                Text(
                    text = " di viaggio",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.neutral500,
                )
            }
            Text(
                // Le linee in fila sono il modo di riconoscere un percorso a
                // colpo d'occhio: prima loro, poi cambi e cammino.
                text = riassunto(o),
                style = MaterialTheme.typography.bodySmall,
                color = c.neutral600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.bodyLarge,
            color = c.neutral400,
        )
    }
}

private fun riassunto(o: OpzioneItinerario): String = buildString {
    append(if (o.lines.isNotEmpty()) o.lines.joinToString(" › ") else "tutto a piedi")
    if (o.rides > 1) {
        val n = o.rides - 1
        append(" · $n camb${if (n == 1) "io" else "i"}")
    }
    if (o.walkMin > 0) append(" · ${o.walkMin} min a piedi")
}
