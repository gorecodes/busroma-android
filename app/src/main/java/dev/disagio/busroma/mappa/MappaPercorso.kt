package dev.disagio.busroma.mappa

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.posizione.Posizione
import dev.disagio.busroma.ui.Spillo
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** Un punto della mappa che si può toccare. */
data class PuntoFermata(
    val stopId: String,
    val nome: String,
    val palina: String?,
    val lat: Double,
    val lon: Double,
)

/** Un mezzo da disegnare, con la direzione se ATAC la dichiara. */
data class PuntoMezzo(
    val id: String,
    val lat: Double,
    val lon: Double,
    val bearing: Double?,
)

private const val SRC_TRACCIATO = "tracciato"
private const val SRC_FERMATE = "fermate"
private const val SRC_MEZZI = "mezzi"
private const val IMG_FRECCIA = "freccia-mezzo"

/** Basalto: il colore delle linee di superficie, che nel GTFS non ne hanno uno. */
private const val BASALTO = "#1B2027"
private const val VERDE_VIVO = "#00875A"
private const val INCHIOSTRO = "#10141A"

/**
 * La mappa di un percorso: tracciato, fermate e mezzi in tempo reale.
 *
 * È la controparte di `RouteMap.tsx` e disegna le stesse tre sorgenti con le
 * stesse regole, perché una mappa che sul telefono mostra cose diverse da
 * quella del browser è una seconda mappa da mantenere, non la stessa.
 *
 * DUE COSE SONO FATTE MEGLIO QUI, e sono possibili solo sul nativo:
 *
 * 1. I MEZZI SI MUOVONO. Sul web la posizione salta ogni quindici secondi e
 *    l'occhio perde quale pallino è quale. Qui la posizione viene interpolata
 *    fra la vecchia e la nuova, così il mezzo scorre lungo la strada e si
 *    capisce dove sta andando anche quando ATAC non dichiara il bearing.
 *
 * 2. IL TOCCO SU UNA FERMATA apre una scheda vera invece di un popup HTML
 *    dentro la tela: il bersaglio è grande come un dito e il testo usa i
 *    caratteri e i colori dell'app.
 *
 * Il resto è parità dichiarata: raggio dei pallini che cresce con lo zoom,
 * colore dal GTFS solo per le metropolitane, inquadratura una volta sola per
 * non litigare con chi trascina.
 */
@Composable
fun MappaPercorso(
    fermate: List<PuntoFermata>,
    mezzi: List<PuntoMezzo>,
    tracciato: List<List<Double>>?,
    coloreLinea: String?,
    modifier: Modifier = Modifier,
    fermataToccata: (PuntoFermata) -> Unit,
) {
    val contesto = LocalContext.current
    // getInstance PRIMA di costruire la MapView, sempre: senza, la MapView
    // esplode al primo disegno con un errore che non nomina questa riga.
    val mapView = remember {
        MapLibre.getInstance(contesto)
        MapView(contesto)
    }
    var mappa by remember { mutableStateOf<MapLibreMap?>(null) }
    var stile by remember { mutableStateOf<Style?>(null) }
    // Si azzera quando cambia il percorso (tracciato o fermate), così ogni verso
    // ottiene la propria prima inquadratura senza strappare la mappa a chi trascina.
    var inquadrata by remember(tracciato, fermate) { mutableStateOf(false) }

    // Catture aggiornate per il listener del click: il LaunchedEffect(mapView)
    // gira una sola volta, ma fermate e fermataToccata cambiano ad ogni verso.
    val fermateAggiornate = rememberUpdatedState(fermate)
    val fermataToccataAggiornata = rememberUpdatedState(fermataToccata)

    val colore = coloreLinea
        ?.takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith("#")) it else "#$it" }
        ?: BASALTO

    // Il ciclo di vita della MapView non si può ignorare: senza onPause/onStop
    // la mappa continua a disegnare e a scaricare tessere in background, che è
    // lo stesso errore che le schermate evitano con repeatOnLifecycle.
    //
    // La vista non chiama nulla a mano: riceve tutto dall'osservatore, ON_CREATE
    // compreso. Lifecycle, quando un osservatore si registra su un proprietario
    // già avviato, gli RECUPERA gli eventi mancanti fino allo stato corrente
    // (la stessa sincronizzazione su cui si basa repeatOnLifecycle) — quindi
    // ON_CREATE/ON_START/ON_RESUME arrivano comunque, nell'ordine giusto, una
    // volta sola. Chiamarli anche a mano nel `remember`, come prima, era la
    // doppia consegna: l'osservatore li ripeteva subito dopo.
    val proprietario = LocalLifecycleOwner.current
    DisposableEffect(proprietario) {
        val osservatore = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        proprietario.lifecycle.addObserver(osservatore)
        onDispose { proprietario.lifecycle.removeObserver(osservatore) }
    }

    // La distruzione è legata alla fine della composizione, non al
    // proprietario: chiavarla su `proprietario`, come prima, avrebbe chiuso
    // una MapView ancora in uso ogni volta che il ciclo di vita cambia
    // proprietario mentre il composable resta in scena — da lì in poi il
    // `remember` avrebbe continuato a restituire una vista morta.
    DisposableEffect(Unit) {
        onDispose { mapView.onDestroy() }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            mappa = m
            m.setStyle(Style.Builder().fromJson(stileMappa())) { s ->
                s.addImage(IMG_FRECCIA, frecciaMezzo())
                s.addSource(GeoJsonSource(SRC_TRACCIATO))
                s.addSource(GeoJsonSource(SRC_FERMATE))
                s.addSource(GeoJsonSource(SRC_MEZZI))

                s.addLayer(
                    LineLayer("tracciato-linea", SRC_TRACCIATO).withProperties(
                        PropertyFactory.lineColor(colore),
                        PropertyFactory.lineWidth(4f),
                        PropertyFactory.lineOpacity(0.85f),
                        PropertyFactory.lineCap("round"),
                        PropertyFactory.lineJoin("round"),
                    ),
                )
                s.addLayer(
                    CircleLayer("fermate-pallini", SRC_FERMATE).withProperties(
                        // Raggio che cresce con lo zoom: da lontano i pallini
                        // si sovrapporrebbero, da vicino devono essere
                        // toccabili. Sono gli stessi tre gradini del web.
                        PropertyFactory.circleRadius(
                            Expression.interpolate(
                                Expression.linear(), Expression.zoom(),
                                Expression.stop(11, 4f),
                                Expression.stop(14, 7f),
                                Expression.stop(16, 9f),
                            ),
                        ),
                        PropertyFactory.circleColor("#ffffff"),
                        PropertyFactory.circleStrokeColor(colore),
                        PropertyFactory.circleStrokeWidth(
                            Expression.interpolate(
                                Expression.linear(), Expression.zoom(),
                                Expression.stop(11, 1.5f),
                                Expression.stop(14, 2.5f),
                                Expression.stop(16, 3f),
                            ),
                        ),
                    ),
                )
                s.addLayer(
                    CircleLayer("mezzi-pallini", SRC_MEZZI).withProperties(
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleColor(VERDE_VIVO),
                        PropertyFactory.circleStrokeColor(INCHIOSTRO),
                        PropertyFactory.circleStrokeWidth(2f),
                    ),
                )
                s.addLayer(
                    SymbolLayer("mezzi-frecce", SRC_MEZZI).withProperties(
                        PropertyFactory.iconImage(IMG_FRECCIA),
                        PropertyFactory.iconRotate(Expression.get("bearing")),
                        // "map" e non "viewport": la freccia deve puntare dove
                        // va il mezzo anche se si ruota la mappa.
                        PropertyFactory.iconRotationAlignment("map"),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconSize(0.9f),
                    ),
                )
                aggiungiStratiPosizione(s)
                stile = s
            }

            m.addOnMapClickListener { punto ->
                val schermo: PointF = m.projection.toScreenLocation(punto)
                // Rettangolo di 44dp attorno al tocco e non il pixel esatto: un
                // pallino da 9 pixel non si becca col dito, e il web se la cava
                // col cursore preciso del mouse.
                val r = 22f * contesto.resources.displayMetrics.density
                val trovate = m.queryRenderedFeatures(
                    RectF(schermo.x - r, schermo.y - r, schermo.x + r, schermo.y + r),
                    "fermate-pallini",
                )
                val f = trovate.firstOrNull()
                if (f != null) {
                    val id = f.getStringProperty("id")
                    val scelta = fermateAggiornate.value.firstOrNull { it.stopId == id }
                    if (scelta != null) {
                        fermataToccataAggiornata.value(scelta)
                        return@addOnMapClickListener true
                    }
                }
                false
            }
        }
    }

    // Tracciato e fermate: si ridisegnano quando cambiano, e RIINQUADRANO solo
    // la prima volta. Reinquadrare a ogni aggiornamento significherebbe
    // strappare la mappa dalle mani di chi la sta trascinando.
    LaunchedEffect(stile, tracciato, fermate) {
        val s = stile ?: return@LaunchedEffect
        val m = mappa ?: return@LaunchedEffect

        tracciato?.takeIf { it.size > 1 }?.let { punti ->
            val linea = LineString.fromLngLats(punti.map { Point.fromLngLat(it[0], it[1]) })
            s.getSourceAs<GeoJsonSource>(SRC_TRACCIATO)?.setGeoJson(linea)
        }

        s.getSourceAs<GeoJsonSource>(SRC_FERMATE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                fermate.map { f ->
                    Feature.fromGeometry(Point.fromLngLat(f.lon, f.lat)).apply {
                        addStringProperty("id", f.stopId)
                        addStringProperty("nome", f.nome)
                    }
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
            val punti = buildList {
                tracciato?.forEach { add(LatLng(it[1], it[0])) }
                fermate.forEach { add(LatLng(it.lat, it.lon)) }
            }
            if (punti.size >= 2) {
                val bordi = LatLngBounds.Builder().includes(punti).build()
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(bordi, 48))
                inquadrata = true
            } else if (punti.size == 1) {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(punti[0], 15.0))
                inquadrata = true
            }
        }
    }

    // Quando il colore della linea arriva dopo lo stile (l'anagrafica scaricata
    // in una coroutine separata) o cambia, aggiorna i layer già costruiti senza
    // ricostruire lo stile, che è un'operazione pesante.
    LaunchedEffect(stile, colore) {
        val s = stile ?: return@LaunchedEffect
        (s.getLayer("tracciato-linea") as? LineLayer)
            ?.setProperties(PropertyFactory.lineColor(colore))
        (s.getLayer("fermate-pallini") as? CircleLayer)
            ?.setProperties(PropertyFactory.circleStrokeColor(colore))
    }

    // I MEZZI SI MUOVONO invece di saltare. Si tiene l'ultima posizione
    // disegnata per ogni mezzo e si interpola verso quella nuova: dodici
    // fotogrammi in ottocento millisecondi bastano perché sembri uno
    // scorrimento e non una teletrasportazione.
    val ultime = remember { mutableMapOf<String, Pair<Double, Double>>() }
    LaunchedEffect(stile, mezzi) {
        val s = stile ?: return@LaunchedEffect
        val sorgente = s.getSourceAs<GeoJsonSource>(SRC_MEZZI) ?: return@LaunchedEffect

        val partenze = mezzi.associate { m ->
            m.id to (ultime[m.id] ?: (m.lat to m.lon))
        }
        val passi = 12
        for (i in 1..passi) {
            val t = i.toFloat() / passi
            sorgente.setGeoJson(
                FeatureCollection.fromFeatures(
                    mezzi.map { m ->
                        val (lat0, lon0) = partenze.getValue(m.id)
                        val lat = lat0 + (m.lat - lat0) * t
                        val lon = lon0 + (m.lon - lon0) * t
                        Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                            addStringProperty("id", m.id)
                            addNumberProperty("bearing", m.bearing ?: 0.0)
                        }
                    },
                ),
            )
            delay(800L / passi)
        }
        mezzi.forEach { ultime[it.id] = it.lat to it.lon }
        // I mezzi spariti dal feed non devono restare in memoria per sempre.
        ultime.keys.retainAll(mezzi.map { it.id }.toSet())
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        BoxScopeDoveSono(mappa, stile, Modifier.align(Alignment.BottomEnd))
    }
}


/**
 * La freccia del mezzo, disegnata in memoria.
 *
 * Generata a runtime come sul web: così non dipende dallo sprite dello stile,
 * che cambia se un giorno si cambia fornitore di tessere.
 */
private fun frecciaMezzo(): Bitmap {
    val lato = 36
    val bmp = Bitmap.createBitmap(lato, lato, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor(INCHIOSTRO)
    }
    val l = lato.toFloat()
    val percorso = Path().apply {
        moveTo(l / 2f, l * 0.11f)
        lineTo(l * 0.83f, l * 0.78f)
        lineTo(l * 0.17f, l * 0.78f)
        close()
    }
    c.drawPath(percorso, p)
    return bmp
}
