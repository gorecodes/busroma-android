package dev.disagio.busroma.percorsi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import dev.disagio.busroma.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val ROMA = ZoneId.of("Europe/Rome")
private val COLONNA = 62.dp

/**
 * Quando partire: adesso, oppure un istante scelto.
 *
 * L'ORARIO CONTA: una linea che a quell'ora non passa non viene proposta,
 * quindi pianificare per dopo dà risultati diversi. Non è un filtro di
 * comodità, è parte della domanda.
 *
 * DUE FINESTRE E NON UNA, al contrario del web. Là c'è `datetime-local`, un
 * campo solo che il browser sa già disegnare; Android non ha l'equivalente, e
 * i due selettori di Material sono separati. Si concatenano: prima il giorno,
 * poi l'ora, e solo alla fine si scrive lo stato — così annullare a metà non
 * lascia una data mezza scelta.
 */
// I selettori di ora di Material 3 sono ancora sperimentali. Si accetta
// l'annotazione invece di disegnarne uno a mano: un selettore d'orario fatto
// in casa sbaglia i casi che contano - ore a 24, cambio dell'ora legale,
// lettori di schermo - e questo li ha gia' risolti.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuandoParti(quando: Long?, imposta: (Long?) -> Unit, c: Palette) {
    var giorno by remember { mutableStateOf<LocalDate?>(null) }
    var scegliData by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PARTI",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = c.neutral500,
            modifier = Modifier.width(COLONNA),
        )
        Text(
            text = quando?.let { etichettaQuando(it) } ?: "Adesso",
            style = MaterialTheme.typography.bodyLarge,
            color = c.neutral900,
            modifier = Modifier.weight(1f),
        )
        if (quando != null) {
            Text(
                text = "Adesso",
                style = MaterialTheme.typography.bodyMedium,
                color = c.brand600,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { imposta(null) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
        Text(
            text = if (quando == null) "Scegli l'ora" else "Cambia",
            style = MaterialTheme.typography.bodyMedium,
            color = c.brand600,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { scegliData = true }
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
    HorizontalDivider(color = c.neutral300)

    if (scegliData) {
        // Si parte dall'istante già scelto, o da adesso: un selettore vuoto
        // costringerebbe a comporre tutto da zero ogni volta.
        val inizio = quando ?: System.currentTimeMillis()
        val stato = rememberDatePickerState(initialSelectedDateMillis = inizio)
        DatePickerDialog(
            onDismissRequest = { scegliData = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = stato.selectedDateMillis
                    scegliData = false
                    if (ms != null) {
                        // selectedDateMillis è mezzanotte UTC del giorno
                        // scelto: si prende solo la data, l'ora arriva dal
                        // secondo selettore. Convertirlo come istante
                        // sposterebbe il giorno di due ore in estate.
                        giorno = Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                }) { Text("Avanti") }
            },
            dismissButton = {
                TextButton(onClick = { scegliData = false }) { Text("Annulla") }
            },
        ) { DatePicker(state = stato) }
    }

    val g = giorno
    if (g != null) {
        val adesso = Instant.ofEpochMilli(quando ?: System.currentTimeMillis()).atZone(ROMA)
        val stato = rememberTimePickerState(
            initialHour = adesso.hour,
            initialMinute = adesso.minute,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { giorno = null }) {
            Surface(shape = RoundedCornerShape(12.dp), color = c.neutral50) {
                Column(Modifier.padding(16.dp)) {
                    TimePicker(state = stato)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { giorno = null }) { Text("Annulla") }
                        TextButton(onClick = {
                            imposta(
                                g.atTime(LocalTime.of(stato.hour, stato.minute))
                                    .atZone(ROMA)
                                    .toInstant()
                                    .toEpochMilli(),
                            )
                            giorno = null
                        }) { Text("Usa questa ora") }
                    }
                }
            }
        }
    }
}
