package dev.disagio.busroma.mappa

import dev.disagio.busroma.dati.Api
import dev.disagio.busroma.dati.OpzioneItinerario
import dev.disagio.busroma.dati.TrattaAPiedi
import dev.disagio.busroma.dati.TrattaInMezzo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sqrt

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
                val punti = percorsoDi(tratta)
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

/**
 * Quanto può stare lontana una fermata dal tracciato perché l'aggancio valga.
 *
 * MISURATO, non scelto a occhio. Su dodici tratte di itinerari veri, dieci
 * agganciano a zero metri: il tracciato passa esattamente per la fermata.
 * Fanno eccezione i nodi di scambio, dove la fermata sta in una corsia
 * diversa da quella del percorso rappresentativo — la 96 a OSTIENSE-PIRAMIDE
 * sta a 171 metri, ed è un aggancio buono. Un aggancio cattivo si è visto a
 * 566 metri su una 8BUS. Duecentocinquanta separa i due casi con margine da
 * tutte e due le parti.
 */
private const val AGGANCIO_MAX_M = 250.0

/**
 * Il tratto agganciato non può essere più corto della linea d'aria fra le due
 * fermate, né molto più lungo.
 *
 * Il primo controllo è quello che smaschera gli agganci sbagliati: un
 * percorso stradale più corto della distanza in linea d'aria è impossibile, e
 * infatti l'unico aggancio cattivo del campione aveva rapporto 0,84. Il
 * secondo tiene fuori i giri larghi: il massimo osservato su un aggancio
 * buono è 1,95, la 982 che fa un anello, quindi tre è largo ma non scemo.
 */
private const val RAPPORTO_MIN = 0.9
private const val RAPPORTO_MAX = 3.0

/**
 * Il pezzo di tracciato percorso da una tratta, o null.
 *
 * SI AGGANCIA PER GEOMETRIA, NON PER IDENTIFICATIVO. La prima versione
 * cercava le due fermate dentro l'elenco della linea e teneva il verso in cui
 * la salita veniva prima della discesa. Funzionava, tranne sulle corse
 * VARIANTE: il percorso della linea è quello di una corsa rappresentativa per
 * verso, e sulla 96 le fermate OSTIENSE-PIRAMIDE e PACINOTTI non compaiono in
 * nessuno dei due. Lì la tratta finiva disegnata come una retta, che è quello
 * che si vedeva sullo schermo.
 *
 * Ora che il server manda le coordinate, il tracciato si aggancia ai PUNTI:
 * si cerca il punto del tracciato più vicino alla fermata di salita e quello
 * più vicino alla discesa, e se sono entrambi abbastanza vicini si taglia fra
 * i due. Una variante che devia per un tratto ma per il resto segue la linea
 * si aggancia lo stesso, ed è il caso normale.
 */
private suspend fun percorsoDi(tratta: TrattaInMezzo): List<List<Double>>? {
    val daLat = tratta.from.lat ?: return null
    val daLon = tratta.from.lon ?: return null
    val aLat = tratta.to.lat ?: return null
    val aLon = tratta.to.lon ?: return null

    for (verso in intArrayOf(0, 1)) {
        val shape = try {
            Api.fermateLinea(tratta.shortName, verso).shape?.coordinates
        } catch (e: Exception) {
            null
        } ?: continue
        if (shape.size < 2) continue

        val i = indicePiuVicino(shape, daLat, daLon)
        val j = indicePiuVicino(shape, aLat, aLon)
        // Il verso giusto e' quello dove si sale PRIMA di scendere.
        if (i >= j) continue
        if (distanzaM(shape[i][1], shape[i][0], daLat, daLon) > AGGANCIO_MAX_M) continue
        if (distanzaM(shape[j][1], shape[j][0], aLat, aLon) > AGGANCIO_MAX_M) continue

        val retta = distanzaM(daLat, daLon, aLat, aLon)
        if (retta > 1.0) {
            var lungo = 0.0
            for (k in i until j) {
                lungo += distanzaM(shape[k][1], shape[k][0], shape[k + 1][1], shape[k + 1][0])
            }
            val rapporto = lungo / retta
            if (rapporto < RAPPORTO_MIN || rapporto > RAPPORTO_MAX) continue
        }

        // I capi esatti in testa e in coda: il punto del tracciato piu' vicino
        // sta comunque a qualche metro dalla fermata, e senza questo la linea
        // non tocca il pallino che le sta accanto.
        return buildList {
            add(listOf(daLon, daLat))
            addAll(shape.subList(i, j + 1))
            add(listOf(aLon, aLat))
        }
    }
    return null
}

/** Distanza in metri, piana: alle distanze di una linea urbana basta. */
private fun distanzaM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = (lat1 - lat2) * 111_320.0
    val dLon = (lon1 - lon2) * 111_320.0 * cos(Math.toRadians((lat1 + lat2) / 2))
    return sqrt(dLat * dLat + dLon * dLon)
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
