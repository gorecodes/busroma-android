package dev.disagio.busroma.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Scala tipografica, con le stesse misure del web.
 *
 * Non sono le dimensioni di serie di Material: sono quelle decise guardando
 * l'app su uno schermo da 360 pixel, dove i nomi di luogo romani sono
 * lunghissimi e ogni punto in più manda una riga a capo.
 *
 * Il carattere è ancora quello di sistema. Barlow — un grottesco disegnato per
 * la segnaletica dei trasporti — arriva in un passo a parte, perché richiede di
 * impacchettare i file del font e non è configurazione.
 *
 * Solo gli stili che usiamo davvero: riempire tutte e quindici le caselle di
 * Material con valori mai visti su uno schermo è lavoro finto.
 */
val Tipografia = Typography(
    // Titolo di pagina: "Fermate", "Percorsi", "Ritardi".
    headlineMedium = TextStyle(
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    ),
    // Nome della fermata nell'intestazione: l'elemento più grande dell'app.
    headlineSmall = TextStyle(
        fontSize = 27.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    // L'attesa in minuti: il dato per cui si apre l'app.
    titleLarge = TextStyle(
        fontSize = 21.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    // Destinazione nella lista degli arrivi.
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 19.sp,
    ),
    // Testo secondario: sottotitoli, spiegazioni.
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // Note, legende, stato del feed.
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // Etichette della navigazione e distintivi di linea.
    labelMedium = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)
