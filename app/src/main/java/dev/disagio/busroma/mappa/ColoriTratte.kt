package dev.disagio.busroma.mappa

import dev.disagio.busroma.dati.OpzioneItinerario
import dev.disagio.busroma.dati.TrattaInMezzo
import kotlin.math.abs

/**
 * I colori con cui distinguere gli autobus di un itinerario.
 *
 * FUORI DA ARANCIO, BLU E VERDE, che sono gli unici tre colori che il GTFS
 * di Roma assegna davvero: MEA #e27439, MEB #0570b5, MEC #008456. Un autobus
 * disegnato in blu accanto alla metro B sarebbe una bugia a colpo d'occhio.
 *
 * Restano viola, magenta, marrone e ardesia: quattro tinte scure, tutte
 * leggibili sulla mappa chiara di OpenStreetMap e distinguibili fra loro
 * anche da chi confonde rosso e verde, perché differiscono in luminosità
 * oltre che in tinta.
 */
private val TAVOLOZZA = listOf(
    "#7B2D8E", // viola
    "#C2185B", // magenta
    "#5D4037", // marrone
    "#37474F", // ardesia
)

/**
 * Quale colore tocca a ogni tratta in mezzo dell'itinerario.
 *
 * IL COLORE QUI NON È L'IDENTITÀ DELLA LINEA, è una legenda. La regola della
 * palette dice che le linee di superficie non hanno un colore ufficiale e
 * inventarne uno è decorazione travestita da dato — e resta vera: sulla
 * pagina di una linea e negli arrivi le targhette restano basalto. Qui il
 * colore dice un'altra cosa, cioè "questa riga dell'elenco è quella tracciata
 * lì sulla mappa", e senza si vedono due linee nere e non si sa quale sia
 * quale.
 *
 * ASSEGNAZIONE DETERMINISTICA dal nome della linea, non dall'ordine: così la
 * 334 tende ad avere lo stesso colore ogni volta che la si incontra, invece
 * di essere viola in un itinerario e magenta in quello dopo. Con una
 * collisione dentro lo stesso itinerario si scorre la tavolozza fino al
 * primo colore libero, perché due tratte dello stesso colore sono
 * esattamente il problema che si sta risolvendo.
 *
 * Le metropolitane non entrano: tengono il colore del GTFS, che è identità
 * vera.
 */
fun coloriTratte(opzione: OpzioneItinerario): Map<Int, String> {
    val esito = mutableMapOf<Int, String>()
    val usati = mutableSetOf<String>()

    opzione.legs.forEachIndexed { indice, tratta ->
        if (tratta !is TrattaInMezzo) return@forEachIndexed
        val ufficiale = tratta.color?.takeIf { it.isNotBlank() }
        if (ufficiale != null) {
            esito[indice] = "#${ufficiale.removePrefix("#")}"
            return@forEachIndexed
        }
        val partenza = abs(tratta.shortName.hashCode()) % TAVOLOZZA.size
        val scelto = (TAVOLOZZA.indices)
            .map { TAVOLOZZA[(partenza + it) % TAVOLOZZA.size] }
            .firstOrNull { it !in usati }
            ?: TAVOLOZZA[partenza]
        usati += scelto
        esito[indice] = scelto
    }
    return esito
}
