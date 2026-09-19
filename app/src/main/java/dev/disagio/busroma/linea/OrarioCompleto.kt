package dev.disagio.busroma.linea

import androidx.compose.runtime.Composable
import dev.disagio.busroma.ui.theme.Palette

/**
 * L'orario completo di una linea a una fermata: tutte le partenze del giorno,
 * non i prossimi novanta minuti.
 *
 * Sul web esiste come interruttore "Tutto l'orario / Solo le prossime" dentro
 * la pagina della linea, e legge le partenze dalla PRIMA fermata del verso.
 * Qui si fa meglio con lo stesso dato: il pannello di una fermata esiste già
 * per ogni fermata del percorso, quindi l'orario si chiede per LA fermata che
 * l'utente ha aperto — che è la domanda vera ("a che ora passa da QUI").
 *
 * [conOggi] distingue oggi da domani: dopo mezzanotte "il primo bus" è una
 * domanda diversa da "l'ultimo", e il server accetta la data.
 */
@Composable
fun OrarioCompleto(
    routeId: String,
    stopId: String,
    verso: Int,
    c: Palette,
): Unit = TODO()
