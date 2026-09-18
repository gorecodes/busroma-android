package dev.disagio.busroma.dati

import dev.disagio.busroma.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Il client delle API di Bus Roma.
 *
 * Le API sono pubbliche e senza autenticazione, e sono modellate sulle
 * schermate: un endpoint per vista. Di solito è un difetto, qui è il vantaggio
 * che permette al client di restare stupido — nessuna logica di dominio a
 * bordo, come deciso in PIANO.md §1.
 */
object Api {

    private val json = Json {
        /**
         * LA RIGA CHE PROTEGGE I TELEFONI GIÀ INSTALLATI.
         *
         * Le API non sono versionate: finché l'unico cliente era il web,
         * aggiungere un campo era gratis perché il web si aggiorna da sé. Con
         * un'app installata, un campo nuovo farebbe fallire la
         * deserializzazione su tutti i telefoni che non hanno aggiornato. Con
         * questa riga, i campi sconosciuti vengono ignorati e l'app continua
         * a funzionare.
         *
         * La controparte sta lato server, e vale da qui in avanti: si
         * aggiungono campi, non si rimuovono e non si rinominano.
         */
        ignoreUnknownKeys = true
        /** Un null dove ci aspettiamo un valore non deve fare esplodere lo schermo. */
        explicitNulls = false
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            // Timeout brevi di proposito: a una fermata, un'attesa di trenta
            // secondi senza risposta e' peggio di un errore immediato, perche'
            // l'utente non sa se aspettare o riprovare.
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 4_000
        }
        defaultRequest {
            /**
             * Ci dichiariamo (PIANO.md §4). Serve a distinguere nei log e nei
             * limiti di frequenza il traffico dell'app da quello del browser,
             * e a poter tagliare un client impazzito senza spegnere il sito.
             */
            header("User-Agent", "BusRoma-Android/${BuildConfig.VERSION_NAME}")
        }
    }

    private val base = BuildConfig.BASE_URL

    /**
     * Arrivi a una fermata. È la chiamata per cui esiste l'app.
     *
     * Gli identificativi di fermata romani sono alfanumerici ("70286", "AD12")
     * ma nulla garantisce che restino privi di caratteri da codificare, quindi
     * il percorso si codifica sempre.
     */
    suspend fun arrivi(stopId: String): RispostaArrivi =
        client.get("$base/api/stops/${stopId.encodeURLPathPart()}/arrivals").body()

    /** Ricerca fermata per nome o numero di palina. */
    suspend fun cercaFermate(query: String): RispostaRicerca =
        client.get("$base/api/stops/search") { parameter("q", query) }.body()

    /** Fermate intorno a una posizione, ordinate per distanza in linea d'aria. */
    suspend fun fermateVicine(lat: Double, lon: Double): RispostaVicine =
        client.get("$base/api/stops/nearby") {
            parameter("lat", lat)
            parameter("lon", lon)
        }.body()
}
