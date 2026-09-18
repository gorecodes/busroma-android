package dev.disagio.busroma.mappa

import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.FermataLinea
import dev.disagio.busroma.dati.OpzioneItinerario
import dev.disagio.busroma.dati.TrattaAPiedi
import dev.disagio.busroma.dati.TrattaInMezzo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Un pezzo di itinerario da disegnare: o si viaggia, o si cammina. */
sealed interface Tratto {
    val punti: List<List<Double>>

    data class Viaggio(
        override val punti: List<List<Double>>,
        /** Il colore ufficiale della linea, se il GTFS ne dichiara uno. */
        val colore: String?,
    ) : Tratto

    data class Cammino(override val punti: List<List<Double>>) : Tratto
}

data class GeometriaItinerario(
    val tratti: List<Tratto> = emptyList(),
    val fermate: List<PuntoFermata> = emptyList(),
    /**
     * Vero solo se OGNI tratta in mezzo ha la sua geometria.
     *
     * Serve a non mentire. Una mappa che disegna la metro e salta l'autobus
     * non e' una mappa parziale, e' una mappa sbagliata: sembra che il
     * viaggio finisca a Piramide. Meglio non mostrarla.
     */
    val completa: Boolean = false,
)

/**
 * Trasforma un itinerario in qualcosa che si possa disegnare su una mappa.
 *
 * SERVE PERCHÉ /api/plan NON DÀ LE COORDINATE. Restituisce le fermate come
 * identificativo, nome e palina: abbastanza per scrivere un elenco, niente
 * per disegnare. La prima idea era chiedere le fermate della corsa a
 * /api/trips, ma quell'endpoint dipende dal tempo reale e con il feed ATAC
 * a terra torna vuoto — cioè proprio quando serve funziona meno.
 *
 * Allora si passa dal percorso della LINEA, che è dato di tabella e c'è
 * sempre: per ogni tratta in mezzo si chiedono le fermate della linea nei due
 * versi e si tiene quello in cui la fermata di salita viene PRIMA di quella
 * di discesa. È un test che non può sbagliare - verificato sulla MEB, dove il
 * verso 0 non contiene nessuna delle due fermate, e sulla 23, dove il verso 1
 * non le contiene.
 *
 * Il nome della linea fa da identificativo di rotta perché a Roma coincidono
 * (`short_name` "23" sta su `route_id` "23", "MEB" su "MEB").
 *
 * IL LIMITE, E NON È RISOLVIBILE DA QUI. Il percorso della linea è quello di
 * una corsa rappresentativa per verso, mentre l'itinerario può usare una
 * VARIANTE che serve fermate diverse. Sulla 96, per dire, le fermate
 * OSTIENSE-PIRAMIDE e PACINOTTI non compaiono in nessuno dei due versi
 * restituiti, pur essendo servite dalla corsa proposta. In quei casi la
 * tratta resta senza geometria e `completa` diventa falso.
 *
 * La soluzione vera sta sul server: /api/plan interroga già la tabella
 * `stops`, basta che selezioni anche le coordinate e le rimandi dentro le
 * fermate dell'itinerario. Tre righe, puramente additive, e questo intero
 * file diventa inutile.
 */
suspend fun geometriaDi(
    opzione: OpzioneItinerario,
    partenza: Pair<Double, Double>?,
    arrivo: Pair<Double, Double>?,
): GeometriaItinerario = withContext(Dispatchers.IO) {
    val tratti = mutableListOf<Tratto>()
    val coordinate = mutableMapOf<String, Pair<Double, Double>>()
    val fermate = mutableListOf<PuntoFermata>()

    // Primo giro: le tratte in mezzo, che sono anche l'unica fonte di
    // coordinate per le fermate.
    val geometrie = mutableMapOf<Int, Tratto.Viaggio>()
    opzione.legs.forEachIndexed { indice, tratta ->
        if (tratta !is TrattaInMezzo) return@forEachIndexed
        val percorso = percorsoDi(tratta) ?: return@forEachIndexed
        geometrie[indice] = Tratto.Viaggio(percorso.punti, tratta.color)
        percorso.fermate.forEach { f ->
            coordinate[f.stopId] = f.lat to f.lon
        }
        fermate += PuntoFermata(
            tratta.from.stopId, tratta.from.name, tratta.from.code,
            coordinate[tratta.from.stopId]?.first ?: return@forEachIndexed,
            coordinate[tratta.from.stopId]?.second ?: return@forEachIndexed,
        )
        coordinate[tratta.to.stopId]?.let { (lat, lon) ->
            fermate += PuntoFermata(tratta.to.stopId, tratta.to.name, tratta.to.code, lat, lon)
        }
    }

    // Secondo giro: nell'ordine dell'itinerario, con i tratti a piedi che
    // chiudono i buchi fra un mezzo e l'altro.
    opzione.legs.forEachIndexed { indice, tratta ->
        when (tratta) {
            is TrattaInMezzo -> geometrie[indice]?.let { tratti += it }
            is TrattaAPiedi -> {
                // Un capo nullo è la partenza o l'arrivo del viaggio: le sue
                // coordinate le sa solo chi ha compilato il modulo, e arrivano
                // da fuori.
                val da = tratta.from?.let { coordinate[it.stopId] } ?: partenza
                val a = tratta.to?.let { coordinate[it.stopId] } ?: arrivo
                if (da != null && a != null) {
                    tratti += Tratto.Cammino(
                        listOf(listOf(da.second, da.first), listOf(a.second, a.first)),
                    )
                }
            }
        }
    }

    val inMezzo = opzione.legs.count { it is TrattaInMezzo }
    GeometriaItinerario(
        tratti = tratti,
        fermate = fermate.distinctBy { it.stopId },
        completa = inMezzo > 0 && geometrie.size == inMezzo,
    )
}

private class Percorso(val punti: List<List<Double>>, val fermate: List<FermataLinea>)

/**
 * Il pezzo di linea effettivamente percorso da una tratta.
 *
 * Si preferisce il TRACCIATO alle fermate quando c'è: una polilinea che passa
 * per le sole fermate taglia le curve e su una linea di superficie si vede
 * che va dritta dentro gli isolati. Il tracciato invece segue le strade, e
 * ritagliarlo fra due fermate costa una ricerca del punto più vicino.
 */
private suspend fun percorsoDi(tratta: TrattaInMezzo): Percorso? {
    for (verso in intArrayOf(0, 1)) {
        val r = try {
            Api.fermateLinea(tratta.shortName, verso)
        } catch (e: Exception) {
            continue
        }
        val ids = r.stops.map { it.stopId }
        val i = ids.indexOf(tratta.from.stopId)
        val j = ids.indexOf(tratta.to.stopId)
        // Il verso giusto è quello dove si sale PRIMA di scendere.
        if (i < 0 || j < 0 || i >= j) continue

        val fermateTratta = r.stops.subList(i, j + 1)
        val shape = r.shape?.coordinates
        val punti = if (shape != null && shape.size > 1) {
            val a = indicePiuVicino(shape, r.stops[i].lat, r.stops[i].lon)
            val b = indicePiuVicino(shape, r.stops[j].lat, r.stops[j].lon)
            if (a <= b) shape.subList(a, b + 1) else shape.subList(b, a + 1).reversed()
        } else {
            fermateTratta.map { listOf(it.lon, it.lat) }
        }
        return Percorso(punti, fermateTratta)
    }
    return null
}

/**
 * L'indice del punto del tracciato più vicino a una fermata.
 *
 * Distanza al quadrato in gradi, senza radice e senza correzione della
 * latitudine: serve solo a ORDINARE punti che distano fra loro qualche
 * decina di metri, e alla latitudine di Roma l'errore non cambia mai quale
 * sia il più vicino.
 */
private fun indicePiuVicino(punti: List<List<Double>>, lat: Double, lon: Double): Int {
    var migliore = 0
    var minimo = Double.MAX_VALUE
    punti.forEachIndexed { i, p ->
        val dx = p[0] - lon
        val dy = p[1] - lat
        val d = dx * dx + dy * dy
        if (d < minimo) {
            minimo = d
            migliore = i
        }
    }
    return migliore
}
