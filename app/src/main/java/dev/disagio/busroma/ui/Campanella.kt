package dev.disagio.busroma.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.ui.theme.LocalPalette

/**
 * Il pulsante campanella sulla riga dell'arrivo.
 *
 * Il bersaglio è 44dp perché il glifo da 20dp è impossibile da toccare con
 * precisione: sul web si era partiti da 30px e si sbagliava mira — la stessa
 * correzione applicata a BellButton.tsx.
 *
 * [inCorso] segnala il momento fra il tocco e il completamento del salvataggio:
 * il pulsante rimane visibile ma sbiadito, e il tocco è disabilitato per evitare
 * doppi avvii.
 */
@Composable
fun Campanella(
    accesa: Boolean,
    inCorso: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPalette.current
    Box(
        modifier = modifier
            .size(44.dp)
            .alpha(if (inCorso) 0.4f else 1f)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = !inCorso, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlifoCampanella(
            piena = accesa,
            colore = if (accesa) c.brand500 else c.neutral400,
            modifier = Modifier.size(20.dp),
        )
    }
}
