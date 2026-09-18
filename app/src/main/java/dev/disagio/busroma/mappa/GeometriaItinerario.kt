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
     * Serve a non mentire: una mappa che disegna la metro e salta l'autobus
     * non e' parziale, e' sbagliata - sembra che il viaggio finisca a
     * Piramide.
     *
     * Da quando le coordinate arrivano dal server e' quasi sempre vera,
     * perche' il ripiego sulla retta non puo' fallire. Resta falsa solo
     * contro un server vecchio che non le manda, e in quel caso la mappa non
     * compare invece di comparire a meta'.
     */
    val completa: Boolean = false,
)

/**
 * Trasforma un itinerario in qualcosa che si possa disegnare su una mappa.
 *
 * LE COORDINATE ORA ARRIVANO DAL SERVER. Prima /api/plan restituiva le
 * fermate come identificativo, nome e palina, e nessun altro endpoint sapeva
 * tradurre un identificativo in coordinate: /api/stops/search non le espone,
 * /api/trips/:id dipende dal tempo reale e col feed ATAC fermo torna vuoto.
 * Toccava indovinarle cercando le due fermate dentro il percorso della linea,
 * e sulle corse VARIANTE non si trovavano - sulla 96, OSTIENSE-PIRAMIDE e
 * PACINOTTI non compaiono in nessuno dei due versi pur essendo servite.
 * Tre righe sul server hanno chiuso il problema alla radice.
 *
 * Quel che resta qui e' solo il DISEGNO DEL PERCORSO, che e' un'altra cosa:
 * fra due fermate ci si arriva per strada, e una retta taglierebbe dentro gli
 * isolati. Quindi si cerca ancora il tracciato della linea per farci passare
 * sopra il tratto, ma adesso e' un miglioramento e non una necessita': se non
 * si trova, i due capi sono noti lo stesso e si unisce con una retta.
 */
suspend fun geometriaDi(
    opzione: OpzioneItinerario,
    partenza: Pair<Double, Double>?,
    arrivo: Pair<Double, Double>?,
): GeometriaItinerario = withContext(Dispatchers.IO) {
    // Le coordinate di ogni fermata nominata dall'itinerario, dritte dal
    // server. Le fermate senza coordinate si saltano: metterle a zero le
    // spedirebbe nel Golfo di Guinea e l'inquadratura comprenderebbe mezzo
    // pianeta.
    val coordinate = mutableMapOf<String, Pair<Double, Double>>()
    val fermate = mutableListOf<PuntoFermata>()
    fun registra(f: dev.disagio.busroma.dati.FermataItinerario?) {
        val lat = f?.lat ?: return
        val lon = f.lon ?: return
        coordinate[f.stopId] = lat to lon
    }
    opzione.legs.forEach { t ->
        when (t) {
            is TrattaInMezzo -> {
                registra(t.from); registra(t.to)
            }
            is TrattaAPiedi -> {
                registra(t.from); registra(t.to)
            }
        }
    }

    val tratti = mutableListOf<Tratto>()
    var inMezzo = 0
    var disegnate = 0

    opzione.legs.forEach { tratta ->
        when (tratta) {
            is TrattaInMezzo -> {
                inMezzo++
                val da = coordinate[tratta.from.stopId]
                val a = coordinate[tratta.to.stopId]
                // Il tracciato della linea se si trova, altrimenti la retta
                // fra i due capi: il percorso e' meno bello ma il viaggio c'e'.
                val punti = percorsoDi(tratta)?.punti
                    ?: if (da != null && a != null) {
                        listOf(listOf(da.second, da.first), listOf(a.second, a.first))
                    } else {
                        null
                    }
                if (punti != null) {
                    tratti += Tratto.Viaggio(punti, tratta.color)
                    disegnate++
                }
                da?.let {
                    fermate += PuntoFermata(
                        tratta.from.stopId, tratta.from.name, tratta.from.code, it.first, it.second,
                    )
                }
                a?.let {
                    fermate += PuntoFermata(
                        tratta.to.stopId, tratta.to.name, tratta.to.code, it.first, it.second,
                    )
                }
            }
            is TrattaAPiedi -> {
                // Un capo nullo e' la partenza o l'arrivo del viaggio: le sue
                // coordinate le sa solo chi ha compilato il modulo.
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

    GeometriaItinerario(
        tratti = tratti,
        fermate = fermate.distinctBy { it.stopId },
        completa = inMezzo > 0 && disegnate == inMezzo,
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
