package dev.disagio.busroma.mappa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlinx.coroutines.delay

private const val SRC_VIAGGI = "itin-viaggi"
private const val SRC_CAMMINI = "itin-cammini"
private const val SRC_NODI = "itin-nodi"
private const val BASALTO = "#1B2027"
private const val FERRO = "#6B7480"

/**
 * La mappa di un itinerario del pianificatore.
 *
 * NON C'È SUL WEB: è la prima cosa che l'app fa e il browser no. L'elenco
 * scritto dice bene quali linee prendere e a che ora, ma non risponde alla
 * domanda che viene subito dopo — "da che parte di Roma passo, e dove
 * cambio". Quella è una domanda geografica e vuole una risposta geografica.
 *
 * Tre livelli, in ordine di lettura:
 * - i tratti IN MEZZO come linee piene, ciascuna col colore della sua linea
 *   se il GTFS ne dichiara uno (le metro), basalto per il resto;
 * - i tratti A PIEDI tratteggiati e in grigio ferro, perché un tratteggio
 *   dice "qui non c'è un mezzo" senza bisogno di una legenda;
 * - i nodi, cioè dove si sale e dove si scende, che sono i punti in cui
 *   sbagliare costa di più.
 *
 * Il colore è dato per feature e non per livello: un itinerario con metro e
 * autobus ha due colori diversi nella stessa sorgente, e moltiplicare i
 * livelli per moltiplicare i colori sarebbe stato il modo lento di farlo.
 */
@Composable
fun MappaItinerario(geometria: GeometriaItinerario, modifier: Modifier = Modifier) {
    val contesto = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(contesto)
        MapView(contesto).apply {
            onCreate(null)
            onStart()
            onResume()
        }
    }
    var mappa by remember { mutableStateOf<MapLibreMap?>(null) }
    var stile by remember { mutableStateOf<Style?>(null) }
    var inquadrata by remember { mutableStateOf(false) }

    val proprietario = LocalLifecycleOwner.current
    DisposableEffect(proprietario) {
        val osservatore = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        proprietario.lifecycle.addObserver(osservatore)
        onDispose {
            proprietario.lifecycle.removeObserver(osservatore)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            mappa = m
            m.setStyle(Style.Builder().fromJson(stileMappa())) { s ->
                s.addSource(GeoJsonSource(SRC_CAMMINI))
                s.addSource(GeoJsonSource(SRC_VIAGGI))
                s.addSource(GeoJsonSource(SRC_NODI))

                // I cammini sotto: dove si sovrappongono a una linea, è la
                // linea che deve restare leggibile.
                s.addLayer(
                    LineLayer("itin-cammini-linea", SRC_CAMMINI).withProperties(
                        PropertyFactory.lineColor(FERRO),
                        PropertyFactory.lineWidth(3f),
                        PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f)),
                        PropertyFactory.lineCap("round"),
                    ),
                )
                s.addLayer(
                    LineLayer("itin-viaggi-linea", SRC_VIAGGI).withProperties(
                        PropertyFactory.lineColor(Expression.get("colore")),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineOpacity(0.9f),
                        PropertyFactory.lineCap("round"),
                        PropertyFactory.lineJoin("round"),
                    ),
                )
                s.addLayer(
                    CircleLayer("itin-nodi-pallini", SRC_NODI).withProperties(
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleColor("#ffffff"),
                        PropertyFactory.circleStrokeColor(BASALTO),
                        PropertyFactory.circleStrokeWidth(2.5f),
                    ),
                )
                stile = s
            }
        }
    }

    LaunchedEffect(stile, geometria) {
        val s = stile ?: return@LaunchedEffect
        val m = mappa ?: return@LaunchedEffect

        s.getSourceAs<GeoJsonSource>(SRC_VIAGGI)?.setGeoJson(
            FeatureCollection.fromFeatures(
                geometria.tratti.filterIsInstance<Tratto.Viaggio>().map { t ->
                    Feature.fromGeometry(
                        LineString.fromLngLats(t.punti.map { Point.fromLngLat(it[0], it[1]) }),
                    ).apply {
                        val c = t.colore?.takeIf { it.isNotBlank() }
                        addStringProperty("colore", if (c == null) BASALTO else "#${c.removePrefix("#")}")
                    }
                },
            ),
        )
        s.getSourceAs<GeoJsonSource>(SRC_CAMMINI)?.setGeoJson(
            FeatureCollection.fromFeatures(
                geometria.tratti.filterIsInstance<Tratto.Cammino>().map { t ->
                    Feature.fromGeometry(
                        LineString.fromLngLats(t.punti.map { Point.fromLngLat(it[0], it[1]) }),
                    )
                },
            ),
        )
        s.getSourceAs<GeoJsonSource>(SRC_NODI)?.setGeoJson(
            FeatureCollection.fromFeatures(
                geometria.fermate.map { f ->
                    Feature.fromGeometry(Point.fromLngLat(f.lon, f.lat))
                },
            ),
        )

        if (!inquadrata) {
            // SI ASPETTA CHE LA VISTA ABBIA UNA DIMENSIONE. Inquadrare su
            // un viewport ancora 0x0 da' uno zoom senza senso: e' lo stesso
            // difetto che sul web e' documentato in RouteMap, dove al primo
            // disegno il contenitore puo' essere ancora alto zero. Qui capita
            // quando la geometria e' pronta prima che Compose abbia misurato
            // la MapView.
            var attese = 0
            while ((mapView.width == 0 || mapView.height == 0) && attese < 40) {
                delay(50)
                attese++
            }
            val punti = geometria.tratti.flatMap { t -> t.punti.map { LatLng(it[1], it[0]) } }
            if (punti.size >= 2) {
                m.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder().includes(punti).build(),
                        56,
                    ),
                )
                inquadrata = true
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
