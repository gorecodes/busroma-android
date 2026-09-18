package dev.disagio.busroma.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.disagio.busroma.R

/**
 * Barlow, lo stesso carattere del web.
 *
 * È un grottesco disegnato sulla segnaletica dei trasporti, ed è la ragione
 * per cui è stato scelto: l'app è un'insegna di fermata, non una brochure.
 *
 * IMPACCHETTATO E NON SCARICABILE. Android offre i "downloadable fonts" dal
 * provider di Google Play Services, che risparmierebbero 750 KB. Scartati: al
 * primo avvio e senza rete il testo comparirebbe col carattere di sistema e
 * poi salterebbe a Barlow, e per un'app che si apre di corsa a una fermata —
 * dove la rete è quella che è — il salto è garantito invece che raro.
 *
 * Licenza OFL, il testo sta in LICENZE/OFL-Barlow.txt.
 */
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

/**
 * La variante semi condensata, per i NOMI DI LUOGO.
 *
 * Non è un vezzo: i capolinea romani sono lunghissimi — "C.SO VITTORIO
 * EMANUELE/S. A. DELLA VALLE" — e nella larghezza normale si troncano sempre.
 * Il condensato guadagna caratteri senza rimpicciolire il testo, che a una
 * fermata al sole è la cosa che conta.
 *
 * Sul web è la classe `.name`, applicata a nomi di fermata e destinazioni.
 * Non ha il peso normale: serve solo dove il testo è in evidenza.
 */
val BarlowCondensato = FontFamily(
    Font(R.font.barlow_cond_medium, FontWeight.Medium),
    Font(R.font.barlow_cond_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_cond_bold, FontWeight.Bold),
)
