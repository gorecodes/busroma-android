package dev.disagio.busroma.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Il tema dell'app: palette, tipografia e aspetto delle barre di sistema.
 *
 * NIENTE COLORE DINAMICO (Material You). Sarebbe una riga di codice, e sarebbe
 * sbagliato: il verde del dato in tempo reale e il rosso ATAC non sono
 * decorazione, sono significato. Se il sistema li ricolora secondo lo sfondo
 * scelto dall'utente, la distinzione fra "mezzo tracciato davvero" e "orario
 * previsto" — che è l'informazione più importante dell'app — diventa una
 * sfumatura casuale.
 */
@Composable
fun TemaBusRoma(
    /**
     * Forza chiaro o scuro ignorando il sistema. È il tasto di cambio tema:
     * il parametro c'era da prima che il tasto esistesse, proprio perché
     * aggiungerlo non dovesse significare toccare il tema.
     */
    scura: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (scura) PaletteScura else PaletteChiara

    val vista = LocalView.current
    if (!vista.isInEditMode) {
        SideEffect {
            val finestra = (vista.context as Activity).window
            // Con il disegno a tutto schermo il sistema non sa se il nostro
            // contenuto è chiaro o scuro, e tiene le icone bianche: su fondo
            // chiaro l'ora nella barra di stato diventa illeggibile. Va detto
            // esplicitamente, e va detto in base al NOSTRO tema — non a quello
            // di sistema, perché i due possono divergere quando si aggiunge
            // il tasto di cambio tema.
            WindowCompat.getInsetsController(finestra, vista).apply {
                isAppearanceLightStatusBars = !scura
                isAppearanceLightNavigationBars = !scura
            }
        }
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = schemaMaterial(palette),
            typography = Tipografia,
            content = content,
        )
    }
}

/**
 * Riempie le caselle di Material 3 con la nostra palette.
 *
 * Serve solo perché i componenti di serie — Scaffold, Text, Surface — non
 * sembrino estranei: per tutto ciò che è espressivo si legge `LocalPalette`.
 * Le caselle che non usiamo restano ai valori di Material: elencarle tutte con
 * colori mai verificati su uno schermo darebbe l'illusione di aver deciso.
 *
 * Nota sulla superficie: `surface` prende neutral50, che in chiaro è più
 * CHIARO della pagina e in scuro è più chiaro del fondo. È l'unico gradino che
 * non si ribalta, per la ragione spiegata in Colori.kt.
 */
private fun schemaMaterial(p: Palette) = if (p.scura) {
    darkColorScheme(
        background = p.neutral100,
        onBackground = p.neutral900,
        surface = p.neutral50,
        onSurface = p.neutral900,
        surfaceVariant = p.neutral200,
        onSurfaceVariant = p.neutral500,
        outline = p.neutral300,
        outlineVariant = p.neutral200,
        primary = p.brand500,
        onPrimary = Color.White,
        primaryContainer = p.brand50,
        onPrimaryContainer = p.brand600,
        // Il rosso ATAC fa anche da colore d'errore: è coerente con la regola
        // del web, dove il rosso è il colore del ritardo.
        error = p.brand500,
        onError = Color.White,
    )
} else {
    lightColorScheme(
        background = p.neutral100,
        onBackground = p.neutral900,
        surface = p.neutral50,
        onSurface = p.neutral900,
        surfaceVariant = p.neutral200,
        onSurfaceVariant = p.neutral500,
        outline = p.neutral300,
        outlineVariant = p.neutral200,
        primary = p.brand500,
        onPrimary = Color.White,
        primaryContainer = p.brand50,
        onPrimaryContainer = p.brand600,
        error = p.brand500,
        onError = Color.White,
    )
}
