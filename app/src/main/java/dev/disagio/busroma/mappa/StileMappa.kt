package dev.disagio.busroma.mappa

import dev.disagio.busroma.BuildConfig

/** Centro di Roma, P.zza Venezia. Gli stessi valori di `lib/mapStyle.ts`. */
const val ROMA_LAT = 41.8955
const val ROMA_LON = 12.4823

/**
 * Lo stile della mappa, con la stessa logica del web.
 *
 * Se c'è una chiave MapTiler si usa il suo stile scuro; altrimenti si ricade
 * sulle tessere raster di OpenStreetMap. MapLibre Native consuma lo stesso
 * JSON di maplibre-gl, quindi le due mappe restano una cosa sola: cambiare
 * fornitore si fa qui e sul web, senza toccare il disegno.
 *
 * ATTENZIONE PRIMA DI PUBBLICARE. Il ripiego su `tile.openstreetmap.org` va
 * bene per sviluppo e traffico basso - è quello che il web dichiara nel suo
 * commento - ma la tile usage policy di OSM non consente a un'app
 * distribuita di usare quelle tessere come base. Prima di mettere l'app su
 * un negozio serve una chiave (MapTiler, Stadia, Protomaps) o le tessere
 * servite dal nostro VPS. Il codice è già pronto per la prima strada: si
 * passa MAPTILER_KEY alla build e non si tocca nient'altro.
 */
fun stileMappa(): String {
    val chiave = BuildConfig.MAPTILER_KEY
    if (chiave.isNotEmpty()) {
        return "https://api.maptiler.com/maps/streets-v2-dark/style.json?key=$chiave"
    }
    // Stile inline invece di un URL: non c'è un JSON pubblico per "raster OSM
    // e nient'altro", e scriverlo qui evita una richiesta in più all'avvio.
    return """
    {
      "version": 8,
      "glyphs": "https://fonts.openmaptiles.org/{fontstack}/{range}.pbf",
      "sources": {
        "osm": {
          "type": "raster",
          "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
          "tileSize": 256,
          "attribution": "© OpenStreetMap"
        }
      },
      "layers": [{ "id": "osm", "type": "raster", "source": "osm" }]
    }
    """.trimIndent()
}
