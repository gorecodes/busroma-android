package dev.disagio.busroma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.ui.theme.LocalPalette

/**
 * Navigazione in basso, come sul web.
 *
 * La ragione è la stessa: ogni ritorno stava IN ALTO, che è il punto più
 * scomodo da raggiungere col pollice su un telefono. Le destinazioni stanno
 * dove la mano già sta.
 *
 * Alte 56dp più l'area di sicurezza, con l'etichetta sotto l'icona: un glifo
 * da solo è ambiguo.
 *
 * TUTTE E QUATTRO, finalmente: l'ultima ad arrivare e' stata Ritardi, e non
 * era li' prima perche' una voce che porta a una schermata vuota e' peggio di
 * una voce assente.
 *
 * L'ORDINE E' QUELLO DEL WEB - Fermate, Percorsi, Ritardi, Preferiti - e non
 * e' un dettaglio: chi usa entrambi impara la posizione col pollice, non
 * l'etichetta, e invertirne due farebbe sbagliare bersaglio.
 */
enum class Sezione(val etichetta: String) {
    Fermate("Fermate"),
    Percorsi("Percorsi"),
    Ritardi("Ritardi"),
    Preferiti("Preferiti"),
}

@Composable
fun NavigazioneBasso(
    /**
     * Nessuna sezione attiva sulle schermate di dettaglio - fermata, linea,
     * avvisi - dove non ci si trova in nessuna delle destinazioni principali.
     * E' lo stesso comportamento del web, dove `path.startsWith` non combacia
     * con niente su `/stop/123` e la barra resta tutta spenta.
     */
    attiva: Sezione?,
    vaiA: (Sezione) -> Unit,
) {
    val c = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(c.neutral50)) {
        HorizontalDivider(color = c.neutral300)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Sezione.entries.forEach { s ->
                val scelta = s == attiva
                // LA VOCE ATTIVA PORTA IL COLORE D'IDENTITA', non l'inchiostro.
                //
                // Era neutral900, e la regola della palette — struttura
                // acromatica, colore solo dove e' un dato — diceva di
                // lasciarla cosi'. Ma quella regola, applicata alla lettera,
                // lascia il colore di marca senza un posto dove stare: dopo
                // essere passati al porpora istituzionale, nell'app non era
                // cambiato niente di visibile perche' il porpora non compariva
                // da nessuna parte. La barra e' CORNICE, non dato: qui il
                // colore dice "sei in questa sezione", che e' esattamente il
                // genere di cosa che l'identita' puo' dire.
                //
                // Si usa il gradino 600 e non il 500: sul fondo in rilievo
                // della barra il 500 in scuro sta a 4,3:1, sotto la soglia per
                // un'etichetta piccola. Il 600 sta a 5,2:1 in scuro e 11,6:1
                // in chiaro.
                val colore = if (scelta) c.brand600 else c.neutral500
                Column(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clickable { vaiA(s) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (s) {
                        Sezione.Fermate -> Palina(colore, Modifier.size(22.dp))
                        Sezione.Percorsi -> Percorso(colore, Modifier.size(22.dp))
                        Sezione.Ritardi -> Cronometro(colore, Modifier.size(22.dp))
                        Sezione.Preferiti -> Stella(scelta, colore, Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = s.etichetta,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (scelta) FontWeight.SemiBold else FontWeight.Normal,
                        color = colore,
                    )
                }
            }
        }
    }
}
