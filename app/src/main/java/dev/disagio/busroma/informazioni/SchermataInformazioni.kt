package dev.disagio.busroma.informazioni

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.disagio.busroma.BuildConfig
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.disagio.busroma.aggiornamenti.Aggiornamenti
import dev.disagio.busroma.aggiornamenti.Esito
import dev.disagio.busroma.ui.BannerAggiornamento
import dev.disagio.busroma.ui.AzioniIntestazione
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Informativa privacy e attribuzioni.
 *
 * DUE OBBLIGHI DIVERSI IN UNA PAGINA SOLA, perché per chi legge sono la
 * stessa domanda — "cosa fa quest'app con i miei dati, e con quelli degli
 * altri".
 *
 * Il primo è l'informativa dell'art. 13 GDPR, dovuta sempre. NON è un
 * consenso: l'app non ha analytics, non profila e non usa identificativi
 * pubblicitari, e le uniche cose che salva sono preferenze chieste
 * esplicitamente. Chiedere un consenso che non serve non è prudenza, è un
 * dark pattern, e il Garante lo ha censurato. Se un domani si aggiunge un
 * analytics, questa pagina va riscritta e serve un vero opt-in.
 *
 * Il secondo sono le attribuzioni: i dati di Roma Capitale sono CC BY-SA,
 * OpenStreetMap è ODbL, e il carattere Barlow è sotto OFL. Tutte e tre
 * chiedono di essere citate, e citarle in fondo a un file di sorgenti non
 * vale.
 *
 * IL TESTO È SPECIFICO DI QUESTA APP e non copiato da quello del web: là si
 * parla di cookie e localStorage, qui di permessi Android e memoria dell'app,
 * e là ci sono le notifiche che qui non esistono ancora. Un'informativa che
 * elenca cose inesistenti è peggio che non averla, perché dichiara il falso.
 */
@Composable
fun SchermataInformazioni(apriAvvisi: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPalette.current
    val contesto = LocalContext.current
    val scope = rememberCoroutineScope()
    var esitoControllo by remember { mutableStateOf<String?>(null) }
    // L'aggiornamento trovato da QUALUNQUE controllo, letto dal deposito:
    // e' lo stesso che vede il banner in home.
    val versioneDisponibile by Aggiornamenti.flussoDisponibile(contesto)
        .collectAsStateWithLifecycle(null)

    fun apri(url: String) {
        contesto.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    Column(modifier.fillMaxSize().background(c.neutral100)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Informazioni",
                style = MaterialTheme.typography.headlineMedium,
                color = c.neutral900,
                modifier = Modifier.weight(1f),
            )
            AzioniIntestazione(apriAvvisi)
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Testo(
                "In breve: nessun account, nessun tracciamento, nessuna pubblicità. " +
                    "Niente di quello che fai qui esce dal telefono, tranne le richieste " +
                    "necessarie a sapere quando passa l'autobus. Sotto il dettaglio, " +
                    "perché «fidati» non è un'informativa.",
                c,
            )

            Titolo("Chi gestisce l'app", c)
            Testo(
                "Bus Roma è un progetto personale di Paolo Pulli, titolare del " +
                    "trattamento dei pochi dati descritti qui sotto. " +
                    "Non è un servizio ufficiale ATAC né di Roma Capitale.",
                c,
            )
            Collegamento("paolo.pulli@proton.me", c) { apri("mailto:paolo.pulli@proton.me") }
            Collegamento("@codingpao su X", c) { apri("https://x.com/codingpao") }

            Titolo("Cosa resta sul telefono", c)
            Testo(
                "Nella memoria dell'app, non su un server: le fermate che hai messo " +
                    "nei preferiti e il loro ordine, le ultime cinque ricerche, e la " +
                    "scelta fra tema chiaro e scuro. Non le vediamo e non le riceviamo. " +
                    "Si cancellano disinstallando l'app, o svuotandone i dati dalle " +
                    "impostazioni di Android.",
                c,
            )

            Titolo("La posizione", c)
            Testo(
                "Serve a due cose: mostrarti le fermate e gli arrivi qui intorno, e " +
                    "usare «la mia posizione» come capo di un percorso o centrarti " +
                    "sulla mappa. Il permesso lo chiede Android la prima volta.",
                c,
            )
            Testo(
                "Da sapere: una volta che il permesso c'è, la posizione viene letta " +
                    "anche senza che tu tocchi niente, all'apertura della sezione «Qui " +
                    "intorno» e quando si disegna una mappa — serve a mostrarti subito " +
                    "le fermate vicine invece di farti premere un tasto ogni volta. Le " +
                    "coordinate servono a quella singola richiesta e non vengono " +
                    "salvate: né sul telefono, né sul nostro server. Il permesso si " +
                    "revoca dalle impostazioni di Android e l'app continua a funzionare " +
                    "senza, tranne quelle due cose.",
                c,
            )

            Titolo("Chi altro viene contattato", c)
            Testo(
                "Il nostro server, bus.disagio.dev, per orari, arrivi, avvisi e " +
                    "percorsi: riceve la richiesta e quindi vede il tuo indirizzo IP, " +
                    "come qualunque sito che apri. Ci dichiariamo con un'etichetta " +
                    "«BusRoma-Android», che serve a distinguere il traffico dell'app da " +
                    "quello del sito.",
                c,
            )
            Testo(
                "OpenStreetMap, quando apri una mappa: le immagini le scarica il " +
                    "telefono direttamente da loro, che in quel momento vedono il tuo " +
                    "IP. Succede solo nelle schermate che hanno una mappa.",
                c,
            )
            Testo(
                "Nessun altro. I caratteri sono dentro l'app, quindi non parte nessuna " +
                    "richiesta a Google, e non c'è Firebase.",
                c,
            )

            Titolo("Cosa non facciamo", c)
            Testo(
                "Niente analytics, niente profilazione, niente identificativo " +
                    "pubblicitario, niente account, niente pubblicità, niente " +
                    "condivisione con terzi. Le notifiche di arrivo, che ci sono sul " +
                    "sito, in questa app ancora non esistono: quando arriveranno questa " +
                    "pagina lo dirà.",
                c,
            )

            Titolo("I tuoi diritti", c)
            Testo(
                "Puoi chiedere accesso, rettifica, cancellazione e limitazione dei " +
                    "dati che ti riguardano (artt. 15-22 GDPR), scrivendo all'indirizzo " +
                    "qui sopra. In pratica non c'è quasi nulla da chiedere, perché non " +
                    "conserviamo niente che ti identifichi. Puoi anche reclamare al " +
                    "Garante per la protezione dei dati personali.",
                c,
            )

            Titolo("Da dove vengono i dati", c)
            Testo(
                "Orari, posizioni dei mezzi e avvisi: Roma Servizi per la Mobilità per " +
                    "Roma Capitale, in formato GTFS e GTFS-Realtime, pubblicati con " +
                    "licenza Creative Commons Attribuzione - Condividi allo stesso modo " +
                    "(CC BY-SA). Bus Roma non è affiliata né approvata da loro, e i " +
                    "numeri che mostra valgono quanto il feed da cui arrivano.",
                c,
            )
            Collegamento("Il dataset sul portale open data di Roma Capitale", c) {
                apri("https://dati.comune.roma.it/catalog/dataset/c_h501-d-9000")
            }
            Testo(
                "Mappe, indirizzi e luoghi: © contributori di OpenStreetMap, dati sotto " +
                    "Open Database License (ODbL).",
                c,
            )
            Collegamento("openstreetmap.org/copyright", c) {
                apri("https://www.openstreetmap.org/copyright")
            }

            Titolo("Software libero", c)
            Testo(
                "Questa app sta in piedi sul lavoro di altri:\n" +
                    "• MapLibre Native, per le mappe — licenza BSD\n" +
                    "• Ktor e OkHttp, per le richieste di rete — Apache 2.0\n" +
                    "• kotlinx.serialization — Apache 2.0\n" +
                    "• AndroidX e Jetpack Compose — Apache 2.0\n" +
                    "• Timber — Apache 2.0\n" +
                    "• Barlow, il carattere — SIL Open Font License 1.1",
                c,
            )

            if (BuildConfig.AGGIORNAMENTI_IN_APP) {
                // L'utente ha premuto un tasto: merita una risposta, anche negativa.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Testo("Versione ${BuildConfig.VERSION_NAME}", c)
                    Text(
                        text = "controlla adesso",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = c.brand600,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                scope.launch {
                                    esitoControllo = null
                                    val esito = Aggiornamenti.controlla(contesto, forzato = true)
                                    esitoControllo = when (esito) {
                                        // Niente testo: sotto compare la
                                        // striscia, che lo dice E offre il
                                        // tasto per prenderlo.
                                        is Esito.Disponibile -> null
                                        Esito.Nessuno -> "Sei aggiornato"
                                        Esito.Errore -> "Non riesco a controllare"
                                    }
                                }
                            }
                            .padding(start = 12.dp, end = 4.dp, top = 13.dp, bottom = 13.dp),
                    )
                }
                esitoControllo?.let { Testo(it, c) }

                // DIRE "DISPONIBILE" SENZA OFFRIRE L'AZIONE ERA UN VICOLO
                // CIECO: da qui si scarica e si installa, con lo stesso
                // componente del banner in home invece di una seconda
                // implementazione da tenere allineata.
                versioneDisponibile?.let { v ->
                    Spacer(Modifier.height(8.dp))
                    BannerAggiornamento(versione = v, allaChiusura = {})
                }
            } else {
                Testo("Versione ${BuildConfig.VERSION_NAME}", c)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Titolo(testo: String, c: Palette) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = c.neutral900,
    )
}

@Composable
private fun Testo(testo: String, c: Palette) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodyMedium,
        color = c.neutral600,
    )
}

@Composable
private fun Collegamento(testo: String, c: Palette, apri: () -> Unit) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = c.brand600,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = apri)
            .padding(vertical = 11.dp),
    )
}
