package dev.disagio.busroma.posizione

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * La posizione, presa dal LocationManager di sistema.
 *
 * NIENTE PLAY SERVICES. `FusedLocationProviderClient` è lo standard di fatto e
 * gestisce meglio i ripieghi, ma aggiunge una dipendenza da Google Play e non
 * funziona sui telefoni che ne sono privi. Per sapere quali fermate hai intorno
 * non serve precisione al metro: basta il provider di sistema, e l'app resta
 * installabile anche senza i servizi Google.
 *
 * SI CHIEDE SOLO AL TOCCO, mai all'avvio. È la stessa regola del web: un'app
 * di trasporti che chiede la posizione appena la apri insegna a negare il
 * permesso per riflesso.
 */
object Posizione {

    fun permessoConcesso(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Una posizione, o null.
     *
     * Prima si guarda l'ULTIMA NOTA, che è immediata: per cercare le fermate
     * nel raggio di qualche centinaio di metri, una posizione di due minuti fa
     * va benissimo e appare subito. Solo se manca o è vecchia si chiede una
     * misura nuova, che accende il GPS e può richiedere secondi.
     *
     * Il timeout è indispensabile: in un cortile interno o in metropolitana la
     * richiesta non torna mai, e senza limite la schermata resterebbe a
     * girare per sempre.
     */
    @SuppressLint("MissingPermission") // verificato da permessoConcesso, chiamato prima
    suspend fun corrente(context: Context): Location? {
        if (!permessoConcesso(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        ultimaUtile(lm)?.let { return it }

        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                val provider = when {
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }
                if (provider == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                val ascoltatore = object : android.location.LocationListener {
                    override fun onLocationChanged(l: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(l)
                    }

                    // Obbligatori prima di API 30, dove sono astratti.
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(null)
                    }
                }

                lm.requestLocationUpdates(provider, 0L, 0f, ascoltatore, context.mainLooper)
                cont.invokeOnCancellation { lm.removeUpdates(ascoltatore) }
            }
        }
    }

    /**
     * L'ultima posizione nota, se non è troppo vecchia.
     *
     * Cinque minuti: oltre, si rischia di mostrare le fermate del posto da cui
     * si è partiti, che è l'errore peggiore possibile qui — sembra
     * un'informazione e non lo è.
     */
    @SuppressLint("MissingPermission")
    private fun ultimaUtile(lm: LocationManager): Location? {
        val soglia = System.currentTimeMillis() - 5 * 60_000
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { p ->
                try {
                    if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null
                } catch (e: SecurityException) {
                    null
                }
            }
            .filter { it.time >= soglia }
            .maxByOrNull { it.time }
    }
}
