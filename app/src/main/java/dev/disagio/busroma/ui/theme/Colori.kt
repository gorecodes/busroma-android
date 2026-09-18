package dev.disagio.busroma.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette "palina", identica a quella del web.
 *
 * I valori esadecimali sono copiati uno per uno da `app/globals.css` del
 * repository AtacWatch, non riscritti a occhio: se i due prodotti divergono sui
 * colori sembrano due app diverse.
 *
 * LA REGOLA CHE TIENE INSIEME TUTTO, la stessa del web: la struttura è
 * acromatica, il colore è un dato. I grigi basalto e ferro portano tutto il
 * testo e le divisioni; il colore appare solo dove significa qualcosa — verde
 * per il dato in tempo reale, rosso per il ritardo e l'identità ATAC, ambra per
 * gli avvisi di servizio. I colori ufficiali delle metropolitane arrivano dal
 * feed GTFS, non da qui.
 *
 * PERCHÉ UNA PALETTE A SCALA E NON I SOLI SLOT DI MATERIAL. Material 3 ha
 * caselle fisse (primary, surface, onSurface...) che non descrivono un sistema
 * a gradini: sul web un testo secondario è `neutral-500` e un filetto è
 * `neutral-300`, e quella granularità va conservata. Gli slot di Material
 * vengono comunque riempiti in `Tema.kt`, ma solo perché i componenti di serie
 * non sembrino estranei: per tutto ciò che è espressivo si usa questa palette.
 *
 * Sono presenti solo i gradini realmente in uso sul web. Aggiungerne altri
 * "per completezza" significherebbe inventare valori mai verificati su uno
 * schermo.
 */
data class Palette(
    // Scala neutra: pietra e basalto, toni freddi.
    val neutral50: Color,
    val neutral100: Color,
    val neutral200: Color,
    val neutral300: Color,
    val neutral400: Color,
    val neutral500: Color,
    val neutral600: Color,
    val neutral700: Color,
    val neutral900: Color,
    val neutral950: Color,
    // Rosso ATAC: non un rosso generico, è il rosso dei mezzi.
    val brand50: Color,
    val brand500: Color,
    val brand600: Color,
    // Verde "dato vivo": denso di proposito, deve reggere il sole a una fermata.
    val live500: Color,
    val live600: Color,
    // Ambra "attenzione": avvisi di servizio e feed stantio.
    val warn50: Color,
    val warn300: Color,
    val warn400: Color,
    val warn500: Color,
    val warn600: Color,
    val warn700: Color,
    /** Serve a chi deve decidere da sé, es. l'aspetto delle barre di sistema. */
    val scura: Boolean,
)

val PaletteChiara = Palette(
    neutral50 = Color(0xFFF7F8F7),
    neutral100 = Color(0xFFEDEFEE), // pietra: sfondo pagina
    neutral200 = Color(0xFFDCE0E0), // filetti e divisori
    neutral300 = Color(0xFFBFC5C7),
    neutral400 = Color(0xFF8E979C), // testo silenzioso
    neutral500 = Color(0xFF6B7480), // ferro: testo secondario
    neutral600 = Color(0xFF4A535F),
    neutral700 = Color(0xFF333C47),
    neutral900 = Color(0xFF1B2027), // basalto: inchiostro
    neutral950 = Color(0xFF10141A),
    brand50 = Color(0xFFFDF1F1),
    brand500 = Color(0xFFC4161C),
    brand600 = Color(0xFFA8121A),
    live500 = Color(0xFF00875A),
    live600 = Color(0xFF006B48),
    warn50 = Color(0xFFFBF3DF),
    warn300 = Color(0xFFDFBE68),
    warn400 = Color(0xFFC99A1E),
    warn500 = Color(0xFFA8740B),
    warn600 = Color(0xFF8A5E08),
    warn700 = Color(0xFF6E4A06),
    scura = false,
)

/**
 * Modalità scura: la scala neutra si RIBALTA, e siccome tutta l'interfaccia usa
 * neutral100 per lo sfondo e neutral900 per l'inchiostro, ogni schermata segue
 * senza sapere nulla del tema.
 *
 * Due cose imparate sul web e riportate qui:
 *
 * Il 50 NON segue il ribaltamento. In chiaro è la superficie in rilievo — le
 * schede, il campo di ricerca — più chiara della pagina; in scuro deve restare
 * in rilievo, quindi più CHIARA dello sfondo. Ribaltandolo meccanicamente
 * finiva sotto al fondo e le schede sembravano incassate.
 *
 * L'inchiostro si ferma prima del bianco: su fondo scuro un bianco pieno vibra
 * e stanca. #d9e1e8 resta sopra i 10:1 di contrasto senza essere una lampadina.
 *
 * E vanno ribaltati ANCHE rosso, verde e ambra: sul web dimenticare l'ambra ha
 * reso gli avvisi illeggibili in modalità scura, perché un colore denso scelto
 * per reggere il sole su fondo chiaro, su fondo scuro diventa una macchia.
 */
val PaletteScura = Palette(
    neutral50 = Color(0xFF242B35), // superficie in rilievo: più chiara della pagina
    neutral100 = Color(0xFF1A1F26), // sfondo pagina
    neutral200 = Color(0xFF2E353F), // filetti: un filo più marcati che in chiaro
    neutral300 = Color(0xFF3C4551),
    neutral400 = Color(0xFF79848E),
    neutral500 = Color(0xFF96A1AC),
    neutral600 = Color(0xFFAFB9C3),
    neutral700 = Color(0xFFC2CBD4),
    neutral900 = Color(0xFFD9E1E8), // inchiostro, fermato prima del bianco
    neutral950 = Color(0xFFE7EDF3),
    brand50 = Color(0xFF2A1416),
    brand500 = Color(0xFFF1545A),
    brand600 = Color(0xFFF3696E),
    live500 = Color(0xFF2FC98A),
    live600 = Color(0xFF47D69A),
    warn50 = Color(0xFF2A2113),
    warn300 = Color(0xFF6D5828),
    warn400 = Color(0xFFB89231),
    warn500 = Color(0xFFE0AE38),
    warn600 = Color(0xFFEBC258),
    warn700 = Color(0xFFF2D179),
    scura = true,
)

/**
 * La palette arriva ai composabili da qui invece che come parametro.
 *
 * `staticCompositionLocalOf` e non `compositionLocalOf`: la palette cambia solo
 * al cambio di tema, cioè raramente, e la variante statica evita di tracciare
 * le letture ricomponendo l'intero albero a ogni accesso.
 */
val LocalPalette = staticCompositionLocalOf { PaletteChiara }
