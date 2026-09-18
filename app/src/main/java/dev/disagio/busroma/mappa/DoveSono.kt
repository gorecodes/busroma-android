package dev.disagio.busroma.mappa

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.disagio.busroma.posizione.Posizione
import dev.disagio.busroma.ui.Spillo
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** L'azzurro con cui ogni mappa del mondo dice "sei qui". Non si reinventa. */
private const val AZZURRO_IO = "#1A73E8"
private const val SRC_IO = "io"

/**
 * Aggiunge alla mappa i livelli della posizione dell'utente.
 *
 * Da chiamare dentro il callback dello stile, dopo gli altri livelli: il
 * pallino di "sei qui" deve stare sopra tracciati e fermate, perché è la
 * cosa che si cerca per prima.
 */
fun aggiungiStratiPosizione(s: Style) {
    s.addSource(GeoJsonSource(SRC_IO))
    // Alone e pallino, nell'ordine: l'alone da solo sembra una macchia, il
    // pallino da solo si perde sulle strade chiare.
    s.addLayer(
        CircleLayer("io-alone", SRC_IO).withProperties(
            PropertyFactory.circleRadius(16f),
            PropertyFactory.circleColor(AZZURRO_IO),
            PropertyFactory.circleOpacity(0.18f),
        ),
    )
    s.addLayer(
        CircleLayer("io-pallino", SRC_IO).withProperties(
            PropertyFactory.circleRadius(6.5f),
            PropertyFactory.circleColor(AZZURRO_IO),
            PropertyFactory.circleStrokeColor("#ffffff"),
            PropertyFactory.circleStrokeWidth(2.5f),
        ),
    )
}

/**
 * Il tasto "dove sono", e il pallino che ne consegue.
 *
 * SI MOSTRA DA SOLO SE IL PERMESSO C'È GIÀ, senza muovere la camera:
 * mostrarlo risponde alla domanda, spostare l'inquadratura senza che nessuno
 * l'abbia chiesto strapperebbe via il percorso che si stava guardando. Il
 * tasto invece sposta, perché l'ha chiesto chi ha premuto.
 *
 * Si appoggia alla cascata in tre passi di [Posizione] e non al
 * LocationComponent di MapLibre: quella cascata esiste perché al chiuso il
 * solo GPS non risponde, ed è un problema già risolto una volta.
 *
 * Sta in un file suo perché lo usano tutte e tre le mappe, e la terza è
 * arrivata dopo: scriverlo una seconda volta voleva dire due posti in cui
 * dimenticarsi di chiedere il permesso.
 */
@Composable
fun BoxScopeDoveSono(
    mappa: MapLibreMap?,
    stile: Style?,
    modifier: Modifier = Modifier,
) {
    val contesto = LocalContext.current
    val scope = rememberCoroutineScope()
    var miaPosizione by remember { mutableStateOf<LatLng?>(null) }
    var cercando by remember { mutableStateOf(false) }
    var negato by remember { mutableStateOf(false) }

    LaunchedEffect(stile, miaPosizione) {
        val s = stile ?: return@LaunchedEffect
        val p = miaPosizione
        s.getSourceAs<GeoJsonSource>(SRC_IO)?.setGeoJson(
            if (p == null) FeatureCollection.fromFeatures(emptyList())
            else FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude))),
            ),
        )
    }

    LaunchedEffect(stile) {
        if (stile != null && Posizione.permessoConcesso(contesto)) {
            Posizione.corrente(contesto)?.let { miaPosizione = LatLng(it.latitude, it.longitude) }
        }
    }

    fun cercami() {
        scope.launch {
            cercando = true
            val l = Posizione.corrente(contesto)
            cercando = false
            if (l != null) {
                val p = LatLng(l.latitude, l.longitude)
                miaPosizione = p
                mappa?.animateCamera(CameraUpdateFactory.newLatLngZoom(p, 15.0), 500)
            }
        }
    }

    val richiesta = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { esiti ->
        if (esiti.values.any { it }) {
            negato = false
            cercami()
        } else {
            negato = true
        }
    }

    Box(
        modifier
            .padding(10.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.92f))
            .clickable {
                if (Posizione.permessoConcesso(contesto)) cercami()
                else richiesta.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Spillo(
            if (negato || cercando) Color(0xFF9AA0A6) else Color(0xFF1A73E8),
            Modifier.size(20.dp),
        )
    }
}
