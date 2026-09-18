package dev.disagio.busroma.ricerca

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.FermataVicina
import dev.disagio.busroma.posizione.Posizione
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome
import kotlinx.coroutines.launch

/**
 * Le fermate qui intorno.
 *
 * IL PERMESSO SI CHIEDE AL TOCCO, mai all'apertura. È la regola del web, e la
 * ragione è pratica: un'app di trasporti che chiede la posizione appena la
 * apri insegna a negare il permesso per riflesso, e poi non lo riottieni più.
 * Qui l'utente tocca un tasto che dice cosa fa, e il sistema chiede subito
 * dopo: il nesso fra la sua azione e la richiesta è evidente.
 *
 * La sezione vive sotto i preferiti nella schermata iniziale, non in una
 * pagina a parte: sono le due cose che servono senza digitare niente.
 */
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

private sealed interface StatoVicine {
    object Riposo : StatoVicine
    object Attesa : StatoVicine
    data class Trovate(
        val fermate: List<FermataVicina>,
        /** Metri di incertezza della posizione usata, se noti. */
        val incertezzaM: Float? = null,
        /** Minuti di età della posizione usata. */
        val etaMin: Int = 0,
    ) : StatoVicine
    data class Errore(val messaggio: String) : StatoVicine
}

@Composable
fun SezioneVicine(apri: (String) -> Unit, c: Palette) {
    val contesto = LocalContext.current
    val scope = rememberCoroutineScope()
    var stato by remember { mutableStateOf<StatoVicine>(StatoVicine.Riposo) }

    fun carica() {
        stato = StatoVicine.Attesa
        scope.launch {
            val pos = Posizione.corrente(contesto)
            if (pos == null) {
                // Distinguere "permesso negato" da "posizione non arrivata" è
                // importante: sono due problemi con due rimedi diversi, e un
                // messaggio unico lascerebbe l'utente a indovinare.
                stato = StatoVicine.Errore(
                    if (Posizione.permessoConcesso(contesto)) {
                        "Non riesco a leggere la posizione. Se sei al chiuso o in metro, capita."
                    } else {
                        "Serve il permesso di posizione."
                    },
                )
                return@launch
            }
            stato = try {
                StatoVicine.Trovate(
                    fermate = Api.fermateVicine(pos.latitude, pos.longitude).stops,
                    incertezzaM = if (pos.hasAccuracy()) pos.accuracy else null,
                    etaMin = ((System.currentTimeMillis() - pos.time) / 60_000L)
                        .coerceAtLeast(0L).toInt(),
                )
            } catch (e: Exception) {
                StatoVicine.Errore("Le fermate vicine non arrivano.")
            }
        }
    }

    // Si chiedono ENTRAMBI i permessi insieme: e' l'unico modo in cui Android
    // mostra all'utente la scelta fra "Precisa" e "Approssimata". Chiedendo
    // solo quella fine il sistema non offrirebbe l'alternativa, e chiedendo
    // solo l'approssimata non si potrebbe mai ottenere la precisa.
    val richiesta = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { esiti ->
        if (esiti.values.any { it }) {
            carica()
        } else {
            stato = StatoVicine.Errore(
                "Senza posizione non posso dirti cosa hai intorno. Cerca la fermata per nome.",
            )
        }
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
                color = c.neutral500,
                modifier = Modifier.weight(1f),
            )
            // AGGIORNA, e non solo nello stato di errore: l'avviso sulla
            // posizione vecchia diceva "ricarica" e non c'era niente da
            // toccare. Serve anche a chi si e' spostato di due fermate.
            if (stato is StatoVicine.Trovate) {
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
            StatoVicine.Riposo -> Tasto("Usa la mia posizione", c) {
                if (Posizione.permessoConcesso(contesto)) {
                    carica()
                } else {
                    richiesta.launch(PERMESSI)
                }
            }

            StatoVicine.Attesa -> Nota("Cerco dove sei…", c)

            is StatoVicine.Errore -> Column {
                Nota(s.messaggio, c)
                Tasto("Riprova", c) {
                    if (Posizione.permessoConcesso(contesto)) {
                        carica()
                    } else {
                        richiesta.launch(PERMESSI)
                    }
                }
            }

            is StatoVicine.Trovate -> if (s.fermate.isEmpty()) {
                Nota("Nessuna fermata nei paraggi. Complimenti, sei nel nulla.", c)
            } else {
                Column {
                    // SI DICHIARA L'INCERTEZZA quando e' grande come il raggio
                    // che stiamo interrogando: mostrare "271 m" calcolati su
                    // una posizione sbagliata di un chilometro sarebbe una
                    // precisione finta, e l'utente non ha modo di accorgersene.
                    val incerta = s.incertezzaM != null &&
                        s.incertezzaM > Posizione.INCERTEZZA_ACCETTABILE_M
                    val vecchia = s.etaMin >= 5
                    val avviso = when {
                        incerta && vecchia ->
                            "Posizione di ${s.etaMin} min fa e approssimata di circa " +
                                "${s.incertezzaM!!.toInt()} m."
                        incerta ->
                            "Posizione approssimata di circa ${s.incertezzaM!!.toInt()} m: " +
                                "l'ordine pu\u00f2 non essere esatto."
                        vecchia ->
                            "Posizione di ${s.etaMin} min fa: se ti sei spostato, ricarica."
                        else -> null
                    }
                    if (avviso != null) {
                        Text(
                            text = avviso,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.warn700,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    s.fermate.take(8).forEach { f ->
                        RigaVicina(f, c) { apri(f.stopId) }
                        HorizontalDivider(color = c.neutral200)
                    }
                }
            }
        }
    }
}

@Composable
private fun RigaVicina(f: FermataVicina, c: Palette, apri: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = apri)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = f.name,
                style = stileNome,
                color = c.neutral900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            f.distanzaM?.let { m ->
                // Metri e non minuti a piedi, di proposito: è una distanza in
                // linea d'aria e tradurla in tempo sarebbe una promessa che
                // non possiamo mantenere. Sul web quella confusione ha
                // prodotto itinerari impossibili.
                Text(
                    text = if (m < 1000) "$m m" else "%.1f km".format(m / 1000.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral500,
                )
            }
        }
        if (f.routes.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = f.routes.take(6).joinToString(" ") +
                    if (f.routes.size > 6) " +${f.routes.size - 6}" else "",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = c.neutral600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

/** Serve a SchermataRicerca per sapere se mostrare il contesto. */
fun permessoPosizione(context: Context) = Posizione.permessoConcesso(context)
