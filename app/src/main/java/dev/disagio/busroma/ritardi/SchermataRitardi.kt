package dev.disagio.busroma.ritardi

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.LineaRitardo
import dev.disagio.busroma.dati.RispostaRitardi
import dev.disagio.busroma.ui.AzioniIntestazione
import dev.disagio.busroma.ui.DistintivoLinea
import dev.disagio.busroma.ui.PieDiPagina
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** I dati cambiano una volta all'ora: non serve inseguirli. */
private const val INTERVALLO_MS = 10 * 60_000L

/** Un'ora di osservazioni è un aneddoto: sotto questa soglia lo si dice. */
private const val ORE_PER_FIDARSI = 24

private val ITALIANO: NumberFormat = NumberFormat.getIntegerInstance(Locale.ITALY)

/**
 * Solo il valore col segno, senza unità: la riga è stretta e va tenuta su una
 * riga sola.
 */
private fun minuti(secondi: Int): String {
    val m = Math.round(secondi / 60.0).toInt()
    if (m == 0) return "0"
    return if (m > 0) "+$m" else "−${-m}"
}

/**
 * Quanto sono puntuali le linee di Roma.
 *
 * SOLA LETTURA E NESSUN CALCOLO A BORDO: il conteggio lo fa il server, una
 * riga per corsa per fascia oraria, con le sue soglie e i suoi scarti. Qui si
 * disegna quello che arriva.
 *
 * LA REGOLA DI QUESTA SCHERMATA, la stessa del web: il campione si dichiara
 * SEMPRE, e prima dei numeri. Una percentuale senza sapere su quante corse
 * poggia non è un dato, è un'opinione con una cifra davanti.
 */
@Composable
fun SchermataRitardi(
    apriAvvisi: () -> Unit,
    apriInformazioni: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPalette.current
    var dati by remember { mutableStateOf<RispostaRitardi?>(null) }
    var errore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun carica() {
        try {
            dati = Api.ritardi()
            errore = false
        } catch (e: Exception) {
            errore = true
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                carica()
                delay(INTERVALLO_MS)
            }
        }
    }

    Column(modifier.fillMaxSize().background(c.neutral100)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ritardi",
                style = MaterialTheme.typography.headlineMedium,
                color = c.neutral900,
            )
            Spacer(Modifier.width(8.dp))
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
            Text(
                text = "Quanto sono puntuali le linee di Roma, misurato giorno per giorno " +
                    "sul feed ATAC. Nessuno lo pubblica: lo contiamo noi.",
                style = MaterialTheme.typography.bodySmall,
                color = c.neutral600,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp),
            )

            val d = dati
            when {
                d == null && errore -> Messaggio(
                    "Le statistiche non si fanno trovare.",
                    c,
                ) { scope.launch { carica() } }
                d == null -> Messaggio("Conto...", c, null)
                else -> Contenuto(d, c)
            }
            PieDiPagina(apriInformazioni)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Contenuto(d: RispostaRitardi, c: Palette) {
    val pochiDati = d.periodo.ore < ORE_PER_FIDARSI

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = buildString {
                append(ITALIANO.format(d.periodo.corse))
                append(" corse osservate in ")
                append(if (d.periodo.ore == 1) "un'ora" else "${d.periodo.ore} ore")
                append(" di servizio.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.neutral600,
        )
        if (pochiDati) {
            Text(
                text = "Ancora pochi dati per trarne conclusioni: con meno di un giorno " +
                    "di raccolta questi numeri raccontano una serata, non un'abitudine.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = c.neutral900,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
    }

    if (d.linee.isEmpty()) {
        Text(
            text = "Nessuna linea ha ancora abbastanza corse osservate. Serve tempo: " +
                "il conteggio va avanti da sé.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.neutral600,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "Meno puntuali",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = c.brand500,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "oltre 5 minuti",
                style = MaterialTheme.typography.bodySmall,
                color = c.neutral400,
            )
        }
        HorizontalDivider(color = c.neutral300)
        d.linee.forEachIndexed { i, l ->
            if (i > 0) HorizontalDivider(color = c.neutral200)
            RigaLinea(l, c)
        }
        HorizontalDivider(color = c.neutral300)
    }

    Column(
        Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // IL METODO SI SPIEGA, e non in fondo a una pagina di aiuto: una
        // classifica di puntualita' e' un'accusa, e chi la legge ha diritto di
        // sapere come e' stata costruita prima di crederci.
        Nota(
            "Come si misura: si guarda lo scostamento dichiarato da ATAC per la prossima " +
                "fermata di ogni corsa in servizio, e ogni corsa pesa una volta per fascia " +
                "oraria — non una volta al minuto, altrimenti un mezzo bloccato nel traffico " +
                "conterebbe quaranta volte. Compaiono solo le linee con almeno " +
                "${d.minCorse} corse osservate.",
            c,
        )
        Nota(
            "\"In ritardo\" vuol dire oltre i 5 minuti: due minuti su un bus urbano non li " +
                "nota nessuno, e diverse linee hanno un orario di tabella ottimista di un " +
                "paio di minuti su cui non ha senso puntare il dito.",
            c,
        )
        Nota(
            "Gli scostamenti oltre i 45 minuti e gli anticipi oltre i 10 sono scartati: nel " +
                "feed ATAC si trovano valori fino a tre ore e mezza, che non sono ritardi ma " +
                "errori di trasmissione.",
            c,
        )
    }
}

@Composable
private fun RigaLinea(l: LineaRitardo, c: Palette) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DistintivoLinea(l.shortName, l.color, l.textColor, larghezza = 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(
                    text = "${l.percRitardo}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = c.neutral900,
                )
                Text(
                    text = " in ritardo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.neutral500,
                )
            }
            Text(
                // L'unita' la porta il primo valore: ripeterla su tutti e due
                // manda la riga a capo su uno schermo stretto.
                text = buildString {
                    append(
                        if (Math.round(l.mediaS / 60.0).toInt() == 0) "in media in orario"
                        else "in media ${minuti(l.mediaS)} min",
                    )
                    append(" · punta ${minuti(l.peggioreS)}")
                    append(" · ${ITALIANO.format(l.corse)} corse")
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.neutral500,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        // Barra proporzionale: a colpo d'occhio dice piu' di una cifra.
        Box(
            Modifier
                .width(48.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(c.neutral200),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(l.percRitardo.coerceIn(0, 100) / 100f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(c.brand500),
            )
        }
    }
}

@Composable
private fun Nota(testo: String, c: Palette) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodySmall,
        color = c.neutral500,
    )
}

@Composable
private fun Messaggio(testo: String, c: Palette, riprova: (() -> Unit)?) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
        Text(
            text = testo,
            style = MaterialTheme.typography.bodyMedium,
            color = c.neutral600,
        )
        if (riprova != null) {
            Text(
                text = "Riprova",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.brand600,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = riprova)
                    .padding(top = 12.dp),
            )
        }
    }
}
