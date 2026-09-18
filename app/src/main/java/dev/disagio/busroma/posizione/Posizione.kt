package dev.disagio.busroma.posizione

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * La posizione, dal LocationManager di sistema.
 *
 * NIENTE PLAY SERVICES. `FusedLocationProviderClient` aggiunge una dipendenza
 * da Google Play e rende l'app non installabile sui telefoni che ne sono
 * privi. E non serve: da Android 12 la PIATTAFORMA ha il suo provider fuso
 * (`LocationManager.FUSED_PROVIDER`), che combina GPS, WiFi e celle
 * esattamente come fa `navigator.geolocation` nel browser.
 *
 * SI CHIEDE LA PRECISA. L'approssimata di Android non è meno accurata:
 * è agganciata a un'area di circa tre chilometri quadrati. Misurato
 * sullo stesso telefono e nello stesso posto: con l'approssimata la fermata
 * più vicina risultava a 271 m, con la precisa a 43 m. Non è una
 * distanza meno esatta, è una fermata diversa.
 *
 * SI CHIEDE SOLO AL TOCCO, mai all'avvio: un'app di trasporti che chiede
 * la posizione appena la apri insegna a negare il permesso per riflesso.
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
     * Oltre questa incertezza la posizione non è utile per ordinare fermate
     * a trecento metri: si mostra comunque, dichiarandola.
     */
    const val INCERTEZZA_ACCETTABILE_M = 300f

    private const val ATTESA_MS = 12_000L

    /**
     * Una posizione, o null. Tre tentativi in cascata.
     *
     * 1. L'ULTIMA NOTA RECENTE (sotto i 5 minuti): immediata.
     * 2. Una MISURA NUOVA da TUTTI i provider disponibili, in parallelo.
     * 3. L'ULTIMA NOTA QUALUNQUE, anche vecchia, dichiarandone l'età:
     *    nello stesso quartiere risponde ancora alla domanda "cosa ho
     *    intorno", e buttarla via per mostrare un errore è la scelta
     *    sbagliata.
     */
    @SuppressLint("MissingPermission") // permessoConcesso viene chiamato prima
    suspend fun corrente(context: Context): Location? {
        if (!permessoConcesso(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        ultimaNota(lm, maxEtaMs = 5 * 60_000L)?.let { return it }

        withTimeoutOrNull(ATTESA_MS) { misuraNuova(context, lm) }?.let { return it }

        return ultimaNota(lm, maxEtaMs = Long.MAX_VALUE)
    }

    /**
     * Chiede una misura a TUTTI i provider attivi CONTEMPORANEAMENTE, e tiene
     * la prima che arriva.
     *
     * QUESTA È LA CORREZIONE DI UN ERRORE. La prima versione sceglieva un
     * solo provider a cascata - "se il GPS è attivo usa il GPS, altrimenti
     * la rete" - e al chiuso non funzionava mai: il GPS È attivo ma non
     * aggancia, quindi si restava ad attenderlo fino al timeout senza mai
     * provare la rete. Nel browser la stessa cosa funzionava, perché
     * navigator.geolocation usa il provider fuso e non uno scelto a mano.
     *
     * L'ordine nella lista conta solo se due rispondono nello stesso
     * istante: il fuso per primo, perché è quello che sa combinare le
     * fonti.
     */
    @SuppressLint("MissingPermission")
    private suspend fun misuraNuova(context: Context, lm: LocationManager): Location? =
        suspendCancellableCoroutine { cont ->
            val provider = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(LocationManager.FUSED_PROVIDER)
                }
                add(LocationManager.NETWORK_PROVIDER)
                add(LocationManager.GPS_PROVIDER)
            }.filter {
                try {
                    lm.isProviderEnabled(it)
                } catch (e: Exception) {
                    false
                }
            }

            if (provider.isEmpty()) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val ascoltatori = mutableListOf<LocationListener>()
            fun smetti() = ascoltatori.forEach { lm.removeUpdates(it) }

            provider.forEach { p ->
                val ascoltatore = object : LocationListener {
                    override fun onLocationChanged(l: Location) {
                        smetti()
                        if (cont.isActive) cont.resume(l)
                    }

                    // Astratti prima di API 30: vanno implementati comunque.
                    override fun onProviderEnabled(provider: String) {}

                    // NON si chiude il tentativo qui: se si spegne il GPS
                    // restano gli altri provider a cui stiamo chiedendo.
                    override fun onProviderDisabled(provider: String) {}
                }
                ascoltatori += ascoltatore
                try {
                    lm.requestLocationUpdates(p, 0L, 0f, ascoltatore, context.mainLooper)
                } catch (e: Exception) {
                    // Un provider che rifiuta non deve impedire agli altri.
                }
            }

            cont.invokeOnCancellation { smetti() }
        }

    /**
     * L'ultima posizione nota entro un'età massima, la più
     * recente fra i provider.
     */
    @SuppressLint("MissingPermission")
    private fun ultimaNota(lm: LocationManager, maxEtaMs: Long): Location? {
        val soglia = if (maxEtaMs == Long.MAX_VALUE) Long.MIN_VALUE
        else System.currentTimeMillis() - maxEtaMs
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.mapNotNull { p ->
            try {
                if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null
            } catch (e: Exception) {
                null
            }
        }
            .filter { it.time >= soglia }
            .maxByOrNull { it.time }
    }
}
