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
 * La palina: targhetta larga in cima, palo che scende dal centro, base.
 *
 * Le proporzioni sono quelle di `StopGlyph` sul web, e non sono un vezzo: la
 * prima versione aveva la targhetta piccola a sinistra e il palo a destra, e a
 * ventidue pixel si leggeva come una BANDIERINA. La targhetta larga sopra il
 * palo centrato e' cio' che la rende riconoscibile come insegna di fermata.
 */
@Composable
fun Palina(colore: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = min(size.width, size.height)
        val spessore = l * 0.085f
        // Targhetta: larga, nella parte alta.
        drawRoundRect(
            color = colore,
            topLeft = Offset(l * 0.18f, l * 0.13f),
            size = androidx.compose.ui.geometry.Size(l * 0.64f, l * 0.36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(l * 0.07f),
            style = Stroke(width = spessore),
        )
        // Palo, dal centro della targhetta verso il basso.
        drawLine(
            color = colore,
            start = Offset(l * 0.50f, l * 0.49f),
            end = Offset(l * 0.50f, l * 0.87f),
            strokeWidth = spessore,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        // Base.
        drawLine(
            color = colore,
            start = Offset(l * 0.32f, l * 0.87f),
            end = Offset(l * 0.68f, l * 0.87f),
            strokeWidth = spessore,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

/** Triangolo d'avviso: il segno universale, leggibile a tredici pixel. */
@Composable
fun Triangolo(colore: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = min(size.width, size.height)
        val sp = l * 0.11f
        val percorso = Path().apply {
            moveTo(l / 2f, l * 0.13f)
            lineTo(l * 0.93f, l * 0.85f)
            lineTo(l * 0.07f, l * 0.85f)
            close()
        }
        drawPath(percorso, colore, style = Stroke(width = sp))
        drawLine(
            colore,
            Offset(l / 2f, l * 0.40f),
            Offset(l / 2f, l * 0.62f),
            strokeWidth = sp,
        )
        drawCircle(colore, radius = sp * 0.6f, center = Offset(l / 2f, l * 0.73f))
    }
}

/** Luna: in tema chiaro, dice che premendo si passa a scuro. */
@Composable
fun Luna(colore: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = min(size.width, size.height)
        // Due cerchi: uno pieno e uno che lo scava. Con drawCircle in
        // sottrazione la falce viene senza costruire un percorso a mano.
        drawCircle(colore, radius = l * 0.42f, center = Offset(l * 0.52f, l * 0.50f))
        drawCircle(
            androidx.compose.ui.graphics.Color.Transparent,
            radius = l * 0.36f,
            center = Offset(l * 0.74f, l * 0.34f),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
        )
    }
}

/** Sole: in tema scuro, dice che premendo si passa a chiaro. */
@Composable
fun Sole(colore: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = min(size.width, size.height)
        val sp = l * 0.09f
        val centro = Offset(l / 2f, l / 2f)
        drawCircle(colore, radius = l * 0.22f, center = centro, style = Stroke(width = sp))
        // Otto raggi, uno ogni 45 gradi.
        for (i in 0 until 8) {
            val a = (i * PI / 4).toFloat()
            val da = l * 0.32f
            val a2 = l * 0.45f
            drawLine(
                colore,
                Offset(centro.x + da * cos(a), centro.y + da * sin(a)),
                Offset(centro.x + a2 * cos(a), centro.y + a2 * sin(a)),
                strokeWidth = sp,
            )
        }
    }
}

/** Una croce: chiude, svuota, annulla. */
@Composable
fun Croce(colore: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = min(size.width, size.height)
        val sp = l * 0.13f
        val a = l * 0.27f
        val b = l * 0.73f
        drawLine(colore, Offset(a, a), Offset(b, b), strokeWidth = sp,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(colore, Offset(b, a), Offset(a, b), strokeWidth = sp,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/**
 * Percorso: due fermi collegati da una strada che gira. È il glifo della
 * sezione Percorsi, ricalcato su `RouteGlyph` del web.
 *
 * Gli angoli sono quadratiche con il controllo nel vertice: la `A` degli
 * archi SVG si riproduce così senza costruire ellissi, e a ventidue pixel la
 * differenza non esiste.
 */
@Composable
fun Percorso(colore: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = min(size.width, size.height)
        fun p(v: Float) = v / 20f * l
        val sp = p(1.6f)
        drawCircle(colore, radius = p(2.2f), center = Offset(p(4.5f), p(15.5f)), style = Stroke(sp))
        drawCircle(colore, radius = p(2.2f), center = Offset(p(15.5f), p(4.5f)), style = Stroke(sp))
        val strada = Path().apply {
            moveTo(p(4.5f), p(13f))
            lineTo(p(4.5f), p(9.5f))
            quadraticTo(p(4.5f), p(7f), p(7f), p(7f))
            lineTo(p(13f), p(7f))
            quadraticTo(p(15.5f), p(7f), p(15.5f), p(4.5f))
        }
        drawPath(
            strada,
            colore,
            style = Stroke(width = sp, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

/**
 * Spillo: "sei qui". Segna il capo del viaggio preso dalla posizione, dove il
 * web usa `PinGlyph`.
 */
@Composable
fun Spillo(colore: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = min(size.width, size.height)
        fun p(v: Float) = v / 20f * l
        val sp = p(1.7f)
        val goccia = Path().apply {
            moveTo(p(10f), p(18f))
            quadraticTo(p(16f), p(12.8f), p(16f), p(8.6f))
            // Mezzo giro sopra, in senso antiorario: chiude la testa dello
            // spillo fra i due fianchi.
            arcTo(
                androidx.compose.ui.geometry.Rect(p(4f), p(2.6f), p(16f), p(14.6f)),
                0f,
                -180f,
                false,
            )
            quadraticTo(p(4f), p(12.8f), p(10f), p(18f))
            close()
        }
        drawPath(goccia, colore, style = Stroke(width = sp))
        drawCircle(colore, radius = p(2.1f), center = Offset(p(10f), p(8.4f)))
    }
}

/** Punta di freccia verso il basso: "qui si apre un elenco". */
@Composable
fun PuntaGiu(colore: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = min(size.width, size.height)
        val sp = l * 0.13f
        drawLine(
            colore,
            Offset(l * 0.22f, l * 0.40f),
            Offset(l * 0.50f, l * 0.66f),
            strokeWidth = sp,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            colore,
            Offset(l * 0.50f, l * 0.66f),
            Offset(l * 0.78f, l * 0.40f),
            strokeWidth = sp,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}
