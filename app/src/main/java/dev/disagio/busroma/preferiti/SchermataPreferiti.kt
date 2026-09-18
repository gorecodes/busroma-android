package dev.disagio.busroma.preferiti

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.disagio.busroma.ui.Stella
import dev.disagio.busroma.ui.theme.LocalPalette
import dev.disagio.busroma.ui.theme.Palette
import dev.disagio.busroma.ui.theme.stileNome
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Altezza fissa di una riga: il riordino ha bisogno di sapere quanto è alta. */
private val ALTEZZA_RIGA = 64.dp

/**
 * La schermata dei preferiti: elenco completo e riordino.
 *
 * PERCHÉ UNA SCHERMATA A SÉ. La prima versione metteva i preferiti in linea
 * nella schermata iniziale, sotto la ricerca. Sbagliato per due motivi: li
 * mischiava alla ricerca, e non lasciava spazio al riordino — che ha bisogno
 * di una lista stabile su cui trascinare, non di una sezione fra altre due.
 * Sul web la divisione è la stessa: la home mostra i primi tre, la gestione
 * sta qui.
 */
@Composable
fun SchermataPreferiti(apri: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPalette.current
    val contesto = LocalContext.current
    val scope = rememberCoroutineScope()
    val salvati by Preferiti.flusso(contesto).collectAsStateWithLifecycle(emptyList())

    Column(modifier.fillMaxSize().background(c.neutral100)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp)) {
            Text(
                text = "Preferiti",
                style = MaterialTheme.typography.headlineMedium,
                color = c.neutral900,
            )
            if (salvati.isNotEmpty()) {
                Text(
                    text = "Tieni premuto e trascina per riordinare · tocca la stella per rimuovere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.neutral500,
                )
            }
        }

        if (salvati.isEmpty()) {
            Text(
                text = "Nessun preferito. Apri una fermata e tocca la stella.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.neutral500,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            return@Column
        }

        ElencoRiordinabile(
            elenco = salvati,
            c = c,
            apri = apri,
            rimuovi = { id -> scope.launch { Preferiti.rimuovi(contesto, id) } },
            sposta = { da, a -> scope.launch { Preferiti.sposta(contesto, da, a) } },
        )
    }
}

/**
 * Riordino per trascinamento, scritto a mano.
 *
 * In Compose non c'è niente di pronto per questo, e le librerie di terze parti
 * per una lista di dieci elementi sarebbero una dipendenza per nulla. La
 * meccanica è quella minima che funziona:
 *
 * - si parte da una PRESSIONE PROLUNGATA e non dal semplice trascinamento,
 *   altrimenti ogni scorrimento della lista sposterebbe una riga;
 * - durante il trascinamento la riga presa segue il dito con uno scostamento
 *   grafico, e le altre NON si muovono: l'anteprima che riordina tutto a ogni
 *   pixel è più elegante e molto più facile da sbagliare;
 * - lo spostamento si applica al rilascio, una volta sola, calcolando di
 *   quante righe ci si è mossi. Riordinare in tempo reale significherebbe
 *   scrivere su DataStore a ogni pixel di movimento.
 */
@Composable
private fun ElencoRiordinabile(
    elenco: List<FermataPreferita>,
    c: Palette,
    apri: (String) -> Unit,
    rimuovi: (String) -> Unit,
    sposta: (Int, Int) -> Unit,
) {
    val densita = LocalDensity.current
    val altezzaPx = with(densita) { ALTEZZA_RIGA.toPx() }

    var presa by remember { mutableIntStateOf(-1) }
    var scostamento by remember { mutableFloatStateOf(0f) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        elenco.forEachIndexed { indice, f ->
            val inMovimento = indice == presa
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ALTEZZA_RIGA)
                    // La riga trascinata va sopra le altre, altrimenti scorre
                    // sotto e sembra sparire.
                    .zIndex(if (inMovimento) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (inMovimento) scostamento else 0f
                    }
                    .background(if (inMovimento) c.neutral50 else c.neutral100)
                    .pointerInput(indice, elenco.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                presa = indice
                                scostamento = 0f
                            },
                            onDrag = { _, delta -> scostamento += delta.y },
                            onDragEnd = {
                                // Si divide per l'altezza della riga per sapere
                                // di quante posizioni ci si e' spostati, e si
                                // arrotonda: meta' riga di movimento conta come
                                // uno spostamento.
                                val salti = (scostamento / altezzaPx).roundToInt()
                                val destinazione = (indice + salti)
                                    .coerceIn(0, elenco.size - 1)
                                if (destinazione != indice) sposta(indice, destinazione)
                                presa = -1
                                scostamento = 0f
                            },
                            onDragCancel = {
                                presa = -1
                                scostamento = 0f
                            },
                        )
                    },
            ) {
                RigaPreferito(f, c, inMovimento, apri = { apri(f.stopId) }, rimuovi = { rimuovi(f.stopId) })
            }
            HorizontalDivider(color = c.neutral200)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RigaPreferito(
    f: FermataPreferita,
    c: Palette,
    inMovimento: Boolean,
    apri: () -> Unit,
    rimuovi: () -> Unit,
) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // L'appiglio: tre righe che dicono "questo si prende". Senza un segno,
        // la pressione prolungata e' una funzione che nessuno scopre.
        Column(
            Modifier.padding(start = 14.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 14.dp, height = 2.dp)
                        .background(if (inMovimento) c.neutral600 else c.neutral400, RoundedCornerShape(1.dp)),
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = apri),
        ) {
            Text(
                text = f.nome,
                style = stileNome,
                color = c.neutral900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            f.palina?.let {
                Text(
                    text = "palina $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral500,
                )
            }
        }

        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = rimuovi),
            contentAlignment = Alignment.Center,
        ) {
            Stella(piena = true, colore = c.brand500, modifier = Modifier.size(22.dp))
        }
    }
}
