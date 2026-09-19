package dev.disagio.busroma.ricerca

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.disagio.busroma.BuildConfig
import dev.disagio.busroma.aggiornamenti.Aggiornamenti
import dev.disagio.busroma.aggiornamenti.Esito
import dev.disagio.busroma.aggiornamenti.VersioneRemota
import dev.disagio.busroma.dati.nomeLinea
import dev.disagio.busroma.storico.Storico
import dev.disagio.busroma.storico.VoceStorico
import dev.disagio.busroma.dati.FermataTrovata
import dev.disagio.busroma.preferiti.CartaPreferito
import dev.disagio.busroma.preferiti.FermataPreferita
import dev.disagio.busroma.preferiti.Preferiti as DepositoPreferiti
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import dev.disagio.busroma.ui.BannerAggiornamento
import dev.disagio.busroma.ui.Croce
import dev.disagio.busroma.ui.AzioniIntestazione
import dev.disagio.busroma.ui.Stella
import dev.disagio.busroma.ui.PieDiPagina
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome
import kotlinx.coroutines.launch

/**
 * La schermata di partenza: ricerca di una fermata.
 *
 * Il titolo dice cosa c'è in pagina — "Fermate" — e non il nome dell'app: sul
 * web era l'unica delle quattro pagine a scrivere "Bus Roma" al posto del
 * proprio contenuto, ed è stato corretto. A chi l'app l'ha già aperta,
 * ripeterle il nome non dice niente.
 *
 * Nessun sottotitolo: il campo di ricerca dichiara già cosa fa.
 */
@Composable
fun SchermataRicerca(
    apriFermata: (stopId: String) -> Unit,
    apriLinea: (routeId: String, verso: Int?) -> Unit,
    apriPreferiti: () -> Unit,
    apriAvvisi: () -> Unit,
    apriInformazioni: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: RicercaViewModel = viewModel()
    val stato by vm.stato.collectAsStateWithLifecycle()
    val c = LocalPalette.current
    val contesto = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferiti by DepositoPreferiti.flusso(contesto).collectAsStateWithLifecycle(emptyList())
    val storico by Storico.flusso(contesto).collectAsStateWithLifecycle(emptyList())
    // Lo storico compare quando si TOCCA il campo, non quando e' vuoto: e' il
    // comportamento di Google e Safari, ed e' quello del web. A campo mai
    // toccato la schermata mostra preferiti e arrivi vicini, che valgono di
    // piu' di un elenco di cose cercate ieri.
    var toccato by remember { mutableStateOf(false) }
    val gestoreFuoco = LocalFocusManager.current
    var versioneDisponibile by remember { mutableStateOf<VersioneRemota?>(null) }
    if (BuildConfig.AGGIORNAMENTI_IN_APP) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val esito = Aggiornamenti.controlla(contesto)
                // Gli errori si tacciono: un controllo fallito non vale un avviso.
                if (esito is Esito.Disponibile) versioneDisponibile = esito.versione
            }
        }
    }

    // L'INDIETRO DI SISTEMA SVUOTA LA RICERCA, non esce dall'app.
    //
    // La ricerca non e' una schermata a se': e' la principale che cambia
    // stato. Quindi l'indietro non aveva niente da chiudere e usciva
    // dall'app - e l'unico modo di tornare alla home era toccare "Fermate" in
    // basso, che l'utente ha giustamente definito non intuitivo. Abilitato
    // solo quando c'e' qualcosa da annullare: altrimenti l'uscita dall'app
    // deve restare possibile.
    BackHandler(enabled = stato.testo.isNotBlank() || toccato) {
        if (stato.testo.isNotBlank()) vm.scrivi("")
        toccato = false
        gestoreFuoco.clearFocus(force = true)
    }

    Column(modifier.fillMaxSize().background(c.neutral100)) {
        if (BuildConfig.AGGIORNAMENTI_IN_APP) {
            versioneDisponibile?.let { versione ->
                BannerAggiornamento(
                    versione = versione,
                    allaChiusura = { versioneDisponibile = null },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Fermate",
                style = MaterialTheme.typography.headlineMedium,
                color = c.neutral900,
                modifier = Modifier.weight(1f),
            )
            AzioniIntestazione(apriAvvisi)
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
            CampoRicerca(
                testo = stato.testo,
                scrivi = vm::scrivi,
                c = c,
                alTocco = { toccato = true },
                svuota = {
                    vm.scrivi("")
                    toccato = false
                    gestoreFuoco.clearFocus(force = true)
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            // Campo toccato e ancora vuoto: lo storico.
            stato.testo.isBlank() && toccato && storico.isNotEmpty() -> Storico(
                voci = storico,
                c = c,
                apriFermata = apriFermata,
                apriLinea = { id -> apriLinea(id, null) },
                svuota = { scope.launch { Storico.svuota(contesto) } },
            )

            // Campo mai toccato: quello che serve senza digitare niente.
            stato.testo.isBlank() -> Column(
                Modifier.verticalScroll(rememberScrollState()),
            ) {
                Preferiti(preferiti, c, apriFermata, apriPreferiti)
                Spacer(Modifier.height(20.dp))
                SezioneVicine(apriFermata, c)
                PieDiPagina(apriInformazioni)
                Spacer(Modifier.height(24.dp))
            }

            stato.errore -> Nota("La ricerca non risponde.", c)
            stato.vuoto && !stato.cercando -> Nota("Nessuna linea e nessuna fermata con questo nome.", c)

            else -> LazyColumn {
                // LE LINEE PRIMA DELLE FERMATE: cercando "90" si vuole la
                // linea 90, non le fermate che hanno 90 nel nome. Sul web
                // vale lo stesso ordine.
                if (stato.linee.isNotEmpty()) {
                    item { Titoletto("Linee", c) }
                    items(stato.linee, key = { "l-" + it.routeId }) { l ->
                        RigaLinea(l, c) {
                            scope.launch {
                                Storico.aggiungi(
                                    contesto,
                                    VoceStorico.Linea(
                                        id = l.routeId,
                                        etichetta = nomeLinea(l.longName, l.type),
                                        shortName = l.shortName,
                                        tipo = l.type,
                                        colore = l.color,
                                        coloreTesto = l.textColor,
                                    ),
                                )
                            }
                            apriLinea(l.routeId, null)
                        }
                        HorizontalDivider(color = c.neutral200)
                    }
                }
                if (stato.fermate.isNotEmpty()) {
                    item { Titoletto("Fermate", c) }
                    items(stato.fermate, key = { "f-" + it.stopId }) { f ->
                        RigaFermata(f, c) {
                            scope.launch {
                                Storico.aggiungi(
                                    contesto,
                                    VoceStorico.Fermata(f.stopId, f.name, f.code),
                                )
                            }
                            apriFermata(f.stopId)
                        }
                        HorizontalDivider(color = c.neutral200)
                    }
                }
            }
        }
    }
}

/**
 * Il campo di ricerca. `BasicTextField` e non `TextField` di Material: quello
 * porta con sé un'etichetta flottante, un'altezza minima di 56dp e
 * un'animazione che qui non servono a niente. Qui serve una riga in cui
 * scrivere.
 */
@Composable
private fun CampoRicerca(
    testo: String,
    scrivi: (String) -> Unit,
    c: Palette,
    alTocco: () -> Unit,
    svuota: () -> Unit,
) {
    val interazioni = remember { MutableInteractionSource() }
    val aFuoco by interazioni.collectIsFocusedAsState()
    LaunchedEffect(aFuoco) { if (aFuoco) alTocco() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.neutral50, RoundedCornerShape(4.dp))
            // L'anello di fuoco porta il colore d'identita' e si ispessisce.
            // Il solo cambio di tinta su un filetto da 1dp non si vede: e' il
            // doppio spessore che dice "sto scrivendo qui".
            .border(
                if (aFuoco) 2.dp else 1.dp,
                if (aFuoco) c.brand500 else c.neutral300,
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = testo,
            onValueChange = scrivi,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(
                MaterialTheme.typography.bodyLarge.copy(color = c.neutral900),
            ),
            cursorBrush = SolidColor(c.neutral900),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            interactionSource = interazioni,
            modifier = Modifier.weight(1f),
            decorationBox = { campo ->
                if (testo.isEmpty()) {
                    Text(
                        "Cerca una fermata",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.neutral400,
                    )
                }
                campo()
            },
        )
        // La croce e' il modo VISIBILE di uscire dalla ricerca. Il gesto
        // indietro fa la stessa cosa, ma un gesto non si vede: senza un segno
        // in pagina, l'unica uscita evidente era la barra in basso.
        if (testo.isNotEmpty()) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = svuota),
                contentAlignment = Alignment.Center,
            ) {
                Croce(c.neutral500, Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun RigaFermata(f: FermataTrovata, c: Palette, apri: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = apri)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(
            text = f.name,
            style = stileNome,
            color = c.neutral900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            f.code?.let {
                Text(
                    text = "palina $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral500,
                )
            }
            if (f.routes.isNotEmpty()) {
                if (f.code != null) {
                    Text(" · ", style = MaterialTheme.typography.bodySmall, color = c.neutral400)
                }
                // Le linee che ci fermano: è l'informazione che fa scegliere fra
                // due paline con lo stesso nome ai due lati della strada.
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
}

@Composable
private fun Nota(testo: String, c: Palette) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(testo, style = MaterialTheme.typography.bodyMedium, color = c.neutral500)
    }
}

/**
 * I preferiti: illimitati e nell'ordine deciso dall'utente.
 *
 * Il riordino per trascinamento non c'e' ancora e il modello lo prevede gia'
 * (Preferiti.sposta): quando arrivera' non servira' migrare i dati di chi ha
 * l'app installata.
 *
 * La stella per rimuovere sta su ogni riga e non dentro un menu: e' l'unica
 * azione distruttiva qui, ed e' immediatamente annullabile ritoccandola dalla
 * pagina della fermata.
 */
@Composable
private fun Preferiti(
    elenco: List<FermataPreferita>,
    c: Palette,
    apri: (String) -> Unit,
    apriTutti: () -> Unit,
) {
    if (elenco.isEmpty()) {
        Nota(
            "Nessun preferito. Apri una fermata e tocca la stella: comparirà qui, " +
                "senza bisogno di cercarla ogni volta.",
            c,
        )
        return
    }

    // TRE, come sul web. La schermata iniziale deve stare sopra la piega: una
    // lista di quindici preferiti spingerebbe le fermate vicine fuori vista.
    val mostrati = elenco.take(3)
    val resto = elenco.size - mostrati.size

    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Preferiti",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                // Etichetta di sezione nel colore d'identita': e' l'intestazione
                // della struttura, non un dato del trasporto.
                color = c.brand500,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (resto > 0) "Vedi tutti (${elenco.size})" else "Gestisci",
                style = MaterialTheme.typography.bodySmall,
                // Gradino 600 come "Aggiorna" nella sezione accanto: i due
                // collegamenti nelle intestazioni di sezione erano di due
                // colori diversi senza motivo.
                color = c.brand600,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = apriTutti)
                    .padding(horizontal = 10.dp, vertical = 13.dp),
            )
        }
        // Nessuna stella di rimozione qui: togliere un preferito e' un'azione
        // di gestione, e la gestione ha la sua schermata. In home la riga fa
        // una cosa sola, aprire la fermata - e mostra i prossimi passaggi,
        // che sono la ragione per cui i preferiti esistono.
        mostrati.forEach { f ->
            CartaPreferito(f, c) { apri(f.stopId) }
            HorizontalDivider(color = c.neutral200)
        }
    }
}

/**
 * Etichetta di sezione, nel colore d'identità.
 *
 * Il colore qui non è decorazione: le etichette di sezione sono
 * l'intestazione della struttura, non un dato del trasporto, e sono il posto
 * dove il porpora istituzionale può comparire senza dire niente di falso.
 */
@Composable
private fun Titoletto(testo: String, c: Palette) {
    Text(
        text = testo,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = c.brand500,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}

/** Una linea fra i risultati: il numero nel riquadro, poi il tipo o il nome. */
@Composable
private fun RigaLinea(l: dev.disagio.busroma.dati.Linea, c: Palette, apri: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = apri)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Distintivo(l.shortName, l.color, l.textColor, c)
        Spacer(Modifier.width(10.dp))
        Text(
            text = nomeLinea(l.longName, l.type),
            style = MaterialTheme.typography.bodyLarge,
            color = c.neutral900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Distintivo(nome: String, colore: String?, coloreTesto: String?, c: Palette) {
    Text(
        text = nome,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = coloreTesto?.let { daEsadecimale(it) } ?: c.neutral100,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .width(46.dp)
            .background(colore?.let { daEsadecimale(it) } ?: c.neutral900, RoundedCornerShape(3.dp))
            .padding(vertical = 4.dp),
    )
}

private fun daEsadecimale(hex: String): Color? {
    val pulito = hex.removePrefix("#")
    if (pulito.length != 6) return null
    return try {
        Color(("ff$pulito").toLong(16))
    } catch (e: Exception) {
        null
    }
}

/**
 * Le ultime cinque ricerche.
 *
 * Si memorizza CIO' CHE VIENE SCELTO, non cio' che viene digitato: "term" e' un
 * testo da ridigitare, una fermata scelta e' un posto dove tornare con un
 * tocco. Per questo ogni voce porta con se' quanto basta a ridisegnarsi senza
 * interrogare il server.
 */
@Composable
private fun Storico(
    voci: List<VoceStorico>,
    c: Palette,
    apriFermata: (String) -> Unit,
    apriLinea: (String) -> Unit,
    svuota: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ultime ricerche",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = c.brand500,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Svuota",
                style = MaterialTheme.typography.bodySmall,
                color = c.brand600,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = svuota)
                    .padding(horizontal = 10.dp, vertical = 13.dp),
            )
        }
        voci.forEach { v ->
            when (v) {
                is VoceStorico.Linea -> Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { apriLinea(v.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Distintivo(v.shortName, v.colore, v.coloreTesto, c)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = v.etichetta,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.neutral900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is VoceStorico.Fermata -> Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { apriFermata(v.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = v.etichetta,
                        style = stileNome,
                        color = c.neutral900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    v.palina?.let {
                        Text(
                            text = "palina $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.neutral500,
                        )
                    }
                }
            }
            HorizontalDivider(color = c.neutral200)
        }
    }
}
