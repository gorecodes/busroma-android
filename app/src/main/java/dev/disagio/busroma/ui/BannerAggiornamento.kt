package dev.disagio.busroma.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import dev.disagio.busroma.aggiornamenti.Aggiornamenti
import dev.disagio.busroma.aggiornamenti.Installatore
import dev.disagio.busroma.aggiornamenti.StatoInstallazione
import dev.disagio.busroma.aggiornamenti.VersioneRemota
import dev.disagio.busroma.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/**
 * Striscia in cima alla home: avvisa della versione nuova solo quando c'è.
 *
 * La chiusura memorizza il versionCode ignorato, non un booleano generico:
 * la versione successiva si ripresenta da sola senza che l'utente debba
 * fare niente.
 */
@Composable
fun BannerAggiornamento(
    versione: VersioneRemota,
    allaChiusura: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPalette.current
    val contesto = LocalContext.current
    val scope = rememberCoroutineScope()
    var stato by remember { mutableStateOf<StatoInstallazione>(StatoInstallazione.Riposo) }

    val inScarico = stato is StatoInstallazione.Scarico

    Column(
        modifier
            .fillMaxWidth()
            .background(c.brand50)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Versione ${versione.versionName} disponibile",
                style = MaterialTheme.typography.bodyMedium,
                color = c.neutral900,
                modifier = Modifier.weight(1f),
            )
            if (!inScarico) {
                Text(
                    text = "Aggiorna",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.brand600,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            scope.launch {
                                avviaAggiornamento(contesto, versione) { stato = it }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 13.dp),
                )
                // La chiusura ignora questa versione; la prossima si riannuncia
                // automaticamente perché si confrontano i versionCode, non un flag.
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            scope.launch {
                                Aggiornamenti.ignora(contesto, versione.versionCode)
                                allaChiusura()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Croce(c.neutral500, Modifier.size(14.dp))
                }
            }
        }
        // L'APK pesa ~30 MB: il silenzio per un minuto sembra un blocco.
        if (inScarico) {
            val percento = (stato as StatoInstallazione.Scarico).percento
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { percento / 100f },
                    modifier = Modifier.weight(1f),
                    color = c.brand500,
                    trackColor = c.neutral200,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$percento%",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.neutral600,
                )
            }
        }
    }
}

private suspend fun avviaAggiornamento(
    contesto: Context,
    versione: VersioneRemota,
    aggiorna: (StatoInstallazione) -> Unit,
) {
    if (!Installatore.permessoConcesso(contesto)) {
        // Prima il permesso: aprire le impostazioni prima che l'utente abbia
        // toccato "aggiorna" fa scattare il riflesso di negare.
        contesto.startActivity(Installatore.intentPermesso(contesto))
        return
    }
    aggiorna(StatoInstallazione.Scarico(0))
    val risultato = Installatore.scaricaEInstalla(contesto, versione) { p ->
        aggiorna(StatoInstallazione.Scarico(p))
    }
    aggiorna(risultato)
}
