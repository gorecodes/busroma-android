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
 * Il carattere è Barlow, impacchettato nell'app (vedi Caratteri.kt). Per i nomi
 * di luogo esiste `stileNome`, che usa la variante semi condensata: i capolinea
 * romani sono lunghissimi e nella larghezza normale si troncano sempre.
 *
 * Solo gli stili che usiamo davvero: riempire tutte e quindici le caselle di
 * Material con valori mai visti su uno schermo è lavoro finto.
 */
val Tipografia = Typography(
    // Titolo di pagina: "Fermate", "Percorsi", "Ritardi".
    headlineMedium = TextStyle(
        fontFamily = Barlow,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    ),
    // Nome della fermata nell'intestazione: l'elemento più grande dell'app.
    headlineSmall = TextStyle(
        fontFamily = Barlow,
        fontSize = 27.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    // L'attesa in minuti: il dato per cui si apre l'app.
    titleLarge = TextStyle(
        fontFamily = Barlow,
        fontSize = 21.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    // Destinazione nella lista degli arrivi.
    bodyLarge = TextStyle(
        fontFamily = Barlow,
        fontSize = 15.sp,
        lineHeight = 19.sp,
    ),
    // Testo secondario: sottotitoli, spiegazioni.
    bodyMedium = TextStyle(
        fontFamily = Barlow,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // Note, legende, stato del feed.
    bodySmall = TextStyle(
        fontFamily = Barlow,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // Etichette della navigazione e distintivi di linea.
    labelMedium = TextStyle(
        fontFamily = Barlow,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)

/**
 * Lo stile dei NOMI DI LUOGO: fermate, capolinea, destinazioni.
 *
 * Non è una casella di Material perché non è una gerarchia tipografica, è una
 * categoria di contenuto — l'equivalente della classe `.name` sul web. Si
 * applica al testo, non al livello.
 */
val stileNome = TextStyle(
    fontFamily = BarlowCondensato,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 19.sp,
    letterSpacing = 0.1.sp,
)
