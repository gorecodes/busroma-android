package dev.disagio.busroma.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * I glifi dell'interfaccia, disegnati a mano.
 *
 * Stessa scelta del web, dove `Glyphs.tsx` sostituisce le emoji con SVG: un
 * glifo disegnato si colora, scala e si legge identico su ogni telefono. In
 * più qui si evita la dipendenza dalle icone di Material, che porterebbe
 * centinaia di vettori per usarne due.
 */

/**
 * La stella dei preferiti. Piena quando la fermata è salvata, contornata
 * quando no: è lo stato, non una decorazione, quindi la differenza deve
 * leggersi senza confronti.
 */
@Composable
fun Stella(
    piena: Boolean,
    colore: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val lato = min(size.width, size.height)
        val raggio = lato / 2f * 0.92f
        val centro = Offset(size.width / 2f, size.height / 2f)
        // 0.382 è il rapporto che dà a una stella a cinque punte le
        // proporzioni canoniche: più grande e le punte si smussano, più
        // piccolo e diventano aghi.
        val raggioInterno = raggio * 0.382f

        val percorso = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) raggio else raggioInterno
            // Si parte da -90 gradi così la punta guarda in alto.
            val angolo = (-PI / 2 + i * PI / 5).toFloat()
            val p = Offset(centro.x + r * cos(angolo), centro.y + r * sin(angolo))
            if (i == 0) percorso.moveTo(p.x, p.y) else percorso.lineTo(p.x, p.y)
        }
        percorso.close()

        if (piena) {
            drawPath(percorso, colore)
        } else {
            drawPath(percorso, colore, style = Stroke(width = lato * 0.09f))
        }
    }
}

/**
 * La palina: palo verticale con la targhetta in cima. È il glifo della
 * sezione "Fermate", e sul web è `StopGlyph`.
 */
@Composable
fun Palina(colore: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = min(size.width, size.height)
        val spessore = l * 0.08f
        // Targhetta: un rettangolo nella metà superiore, spostato a sinistra
        // perché il palo passa a destra - come le paline vere.
        drawRoundRect(
            color = colore,
            topLeft = Offset(l * 0.12f, l * 0.13f),
            size = androidx.compose.ui.geometry.Size(l * 0.58f, l * 0.36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(l * 0.06f),
            style = Stroke(width = spessore),
        )
        // Palo e base.
        drawLine(
            color = colore,
            start = Offset(l * 0.70f, l * 0.13f),
            end = Offset(l * 0.70f, l * 0.87f),
            strokeWidth = spessore,
        )
        drawLine(
            color = colore,
            start = Offset(l * 0.50f, l * 0.87f),
            end = Offset(l * 0.90f, l * 0.87f),
            strokeWidth = spessore,
        )
    }
}
