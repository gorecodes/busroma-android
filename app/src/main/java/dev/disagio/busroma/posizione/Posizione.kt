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
 * fonde meglio GPS, rete e sensori, ma aggiunge una dipendenza da Google Play
 * e rende l'app non installabile sui telefoni che ne sono privi. Il
 * LocationManager di sistema dà lo stesso dato del GPS: quello che si perde è
 * la fusione dei sensori, che conta per il navigatore di un'auto in
 * movimento, non per sapere a quale palina sei vicino stando fermo.
 *
 * SI CHIEDE SOLO AL TOCCO, mai all'avvio. È la stessa regola del web: un'app
 * di trasporti che chiede la posizione appena la apri insegna a negare il
 * permesso per riflesso.
 *
 * SI CHIEDE LA PRECISA. L'approssimata di Android non è meno accurata: è
 * agganciata a un'area di circa tre chilometri quadrati. Su una lista di
 * fermate entro trecento metri quel dato è falso, e falso in modo verosimile —
 * la schermata mostrerebbe "271 m" calcolati su una posizione sbagliata di un
 * chilometro. Se l'utente concede solo l'approssimata l'app funziona comunque,
 * ma l'incertezza dichiarata dal sistema (`Location.accuracy`) viene mostrata
 * in pagina invece di far finta che il dato sia buono.
 */
object Posizione {

    /** Un permesso qualunque, anche solo l'approssimato: basta a provarci. */
    fun permessoConcesso(context: Context): Boolean =
        concesso(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
            concesso(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** La precisa, l'unica che rende sensata una lista per distanza. */
    fun permessoPreciso(context: Context): Boolean =
        concesso(context, Manifest.permission.ACCESS_FINE_LOCATION)

    private fun concesso(context: Context, permesso: String) =
        ContextCompat.checkSelfPermission(context, permesso) == PackageManager.PERMISSION_GRANTED

    /**
     * Oltre questa incertezza la posizione non e' utile per ordinare fermate a
     * trecento metri: si mostra comunque, ma dicendo che e' approssimata.
     * 300 metri perche' e' l'ordine di grandezza del raggio che interroghiamo:
     * se l'errore e' grande come il raggio, il primo risultato puo' facilmente
     * non essere il piu' vicino.
     */
    const val INCERTEZZA_ACCETTABILE_M = 300f

    /**
     * Una posizione, o null. Tre tentativi in cascata.
     *
     * 1. L'ULTIMA NOTA RECENTE, sotto i cinque minuti: è immediata, e per
     *    cercare fermate nel raggio di qualche centinaio di metri va benissimo.
     * 2. Una MISURA NUOVA, che accende il GPS e può richiedere secondi. Il
     *    timeout è indispensabile: in un cortile interno o in metropolitana la
     *    richiesta non torna mai, e senza limite la schermata resterebbe a
     *    girare per sempre.
     * 3. L'ULTIMA NOTA QUALUNQUE, anche vecchia. Questo terzo tentativo è
     *    stato aggiunto dopo averlo provato sul telefono: al chiuso il GPS non
     *    aggancia in dieci secondi, e quel caso sarà la norma, non
     *    l'eccezione. Buttare via una posizione di dieci minuti fa per
     *    mostrare un errore è la scelta sbagliata — nello stesso quartiere
     *    quella posizione risponde ancora alla domanda "cosa ho intorno".
     *    L'età viene restituita al chiamante, che la dichiara in pagina: un
     *    dato vecchio dichiarato è utile, un dato vecchio spacciato per fresco
     *    è una bugia.
     */
    @SuppressLint("MissingPermission") // verificato da permessoConcesso, chiamato prima
    suspend fun corrente(context: Context): Location? {
        if (!permessoConcesso(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        ultimaUtile(lm)?.let { return it }

        val fresca = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                // Il GPS PRIMA della rete, al contrario di prima: col permesso
                // preciso e' il provider che da' il dato che ci serve. La rete
                // resta come ripiego, perche' al chiuso il GPS non aggancia.
                val provider = when {
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
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
        if (fresca != null) return fresca

        // Terzo tentativo: meglio vecchia che niente, e l'eta' la dichiara chi
        // la mostra.
        return ultimaQualunque(lm)
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

    /**
     * L'ultima posizione nota senza limiti di età: l'ultima spiaggia quando il
     * GPS non aggancia. Chi la usa deve guardare `Location.time` e dire
     * quanto è vecchia.
     */
    @SuppressLint("MissingPermission")
    private fun ultimaQualunque(lm: LocationManager): Location? =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { p ->
                try {
                    if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null
                } catch (e: SecurityException) {
                    null
                }
            }
            .maxByOrNull { it.time }
}
