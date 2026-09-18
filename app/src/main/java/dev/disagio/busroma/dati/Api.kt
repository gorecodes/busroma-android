package dev.disagio.busroma.dati

import dev.disagio.busroma.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
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
/**
 * Nessun itinerario fra i due capi.
 *
 * Eccezione a sé e non un ritorno nullo perché NON è un guasto: /api/plan
 * risponde 404 anche quando ha lavorato bene e la risposta è "da qui non ci
 * arrivi". Confonderla con un errore di rete farebbe mostrare "riprova" a chi
 * invece deve cambiare destinazione.
 */
class NessunItinerario : Exception("nessun itinerario trovato")

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
        /**
         * Il campo che distingue le due forme di `Tratta` nell'itinerario.
         *
         * Si imposta qui e non con @JsonClassDiscriminator sulla gerarchia
         * perché quell'annotazione è ancora sperimentale. Vale per tutta
         * l'istanza Json, e va bene: `Tratta` è l'unica gerarchia sigillata
         * che arriva dalle API.
         */
        classDiscriminator = "kind"
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

    /**
     * Una singola corsa: le sue fermate con gli orari, e il mezzo se c'e'.
     *
     * Gli identificativi di corsa contengono un cancelletto ("0#4692-12"),
     * quindi la codifica del percorso non e' un'ipotesi di scuola: senza,
     * tutto dopo il cancelletto verrebbe letto come frammento e la richiesta
     * arriverebbe troncata.
     */
    suspend fun corsa(tripId: String): RispostaCorsa =
        client.get("$base/api/trips/${tripId.encodeURLPathPart()}").body()

    /** Freschezza del feed ATAC: non del browser, del worker. */
    suspend fun statoFeed(): StatoFeedDto =
        client.get("$base/api/status").body()

    /** Tutti gli avvisi attivi. */
    suspend fun avvisi(): RispostaAvvisi =
        client.get("$base/api/alerts").body()

    /**
     * I prossimi passaggi di UNA linea a UNA fermata.
     *
     * Serve al pannello che si apre toccando una fermata nell'elenco del
     * percorso: la domanda lì non è "cosa passa da qui" ma "quando passa
     * QUESTA linea da qui", e sono due risposte diverse.
     */
    suspend fun passaggiLineaAllaFermata(routeId: String, stopId: String): RispostaPassaggiLinea =
        client.get("$base/api/routes/${routeId.encodeURLPathPart()}/arrivals") {
            parameter("stop", stopId)
        }.body()

    /** Gli avvisi delle linee che servono una fermata. */
    suspend fun avvisiFermata(stopId: String): RispostaAvvisi =
        client.get("$base/api/alerts") { parameter("stop", stopId) }.body()

    /** Gli avvisi di una linea. */
    suspend fun avvisiLinea(routeId: String): RispostaAvvisi =
        client.get("$base/api/alerts") { parameter("route", routeId) }.body()

    /** Anagrafica della linea e i suoi versi. */
    suspend fun linea(routeId: String): RispostaLinea =
        client.get("$base/api/routes/${routeId.encodeURLPathPart()}").body()

    /** Le fermate di un verso, in ordine di percorso. */
    suspend fun fermateLinea(routeId: String, verso: Int): RispostaFermateLinea =
        client.get("$base/api/routes/${routeId.encodeURLPathPart()}/stops") {
            parameter("dir", verso)
        }.body()

    /** I mezzi attualmente localizzati su un verso. */
    suspend fun mezziLinea(routeId: String, verso: Int): RispostaMezzi =
        client.get("$base/api/routes/${routeId.encodeURLPathPart()}/live") {
            parameter("dir", verso)
        }.body()

    /** Ricerca linea per numero o nome. */
    suspend fun cercaLinee(query: String): RispostaLinee =
        client.get("$base/api/routes") { parameter("q", query) }.body()

    /** Ricerca fermata per nome o numero di palina. */
    suspend fun cercaFermate(query: String): RispostaRicerca =
        client.get("$base/api/stops/search") { parameter("q", query) }.body()

    /** Fermate intorno a una posizione, ordinate per distanza in linea d'aria. */
    suspend fun fermateVicine(lat: Double, lon: Double): RispostaVicine =
        client.get("$base/api/stops/nearby") {
            parameter("lat", lat)
            parameter("lon", lon)
        }.body()

    /**
     * Gli ARRIVI alle fermate vicine: cosa si puo' prendere adesso.
     *
     * Diverso da `fermateVicine`, che dice solo dove sono le paline. In strada
     * la domanda e' la prima, e sul web la sezione "Qui intorno" mostra questa.
     */
    suspend fun arriviVicini(lat: Double, lon: Double): RispostaArriviVicini =
        client.get("$base/api/nearby/arrivals") {
            parameter("lat", lat)
            parameter("lon", lon)
        }.body()

    /**
     * Ricerca unificata per un capo del viaggio: fermate del GTFS e luoghi di
     * OpenStreetMap nello stesso elenco.
     *
     * Il filtro sui tre caratteri sta anche qui e non solo sul server: una
     * richiesta che si sa inutile non si manda.
     */
    suspend fun geocodifica(query: String): RispostaGeocodifica {
        val q = query.trim()
        if (q.length < 3) return RispostaGeocodifica()
        return client.get("$base/api/geocode") { parameter("q", q) }.body()
    }

    /**
     * Un itinerario fra due capi.
     *
     * Ogni capo è O una fermata O una coppia di coordinate, mai entrambe: il
     * server accetta le due forme e risolve lui le coordinate di una fermata.
     * Per questo i parametri sono tutti opzionali e si mandano solo quelli
     * valorizzati - `parameter` di Ktor salta i null da sé.
     *
     * Il 404 NON è un guasto: significa che il calcolo è andato a buon fine e
     * la risposta è "da qui non ci arrivi". Si traduce in [NessunItinerario]
     * perché la schermata possa dire la cosa giusta.
     */
    suspend fun pianifica(
        daFermata: String? = null,
        daLat: Double? = null,
        daLon: Double? = null,
        aFermata: String? = null,
        aLat: Double? = null,
        aLon: Double? = null,
        quandoIso: String? = null,
    ): Piano {
        val r = client.get("$base/api/plan") {
            /**
             * TIMEOUT LUNGO, SOLO QUI.
             *
             * Gli 8 secondi di default esistono perché a una fermata
             * un'attesa muta è peggio di un errore immediato. Il
             * pianificatore è il caso opposto: la PRIMA richiesta dopo che il
             * server è stato fermo carica orari e collegamenti del giorno e
             * può passare i 10 secondi; dalla seconda risponde in un quarto
             * di secondo perché se li tiene. Con il timeout breve il primo
             * tentativo mostrava sempre "il calcolo si è arreso" - e il
             * secondo funzionava, che è il modo peggiore di fallire, perché
             * sembra che l'app sia rotta a caso.
             *
             * Qui l'attesa è accettabile: l'utente ha chiesto un calcolo e il
             * tasto dice "Calcolo…", mentre agli arrivi non ha chiesto niente
             * e sta guardando uno schermo che dovrebbe già avere i numeri.
             */
            timeout { requestTimeoutMillis = 25_000 }
            parameter("fromStopId", daFermata)
            parameter("fromLat", daLat)
            parameter("fromLon", daLon)
            parameter("toStopId", aFermata)
            parameter("toLat", aLat)
            parameter("toLon", aLon)
            parameter("at", quandoIso)
        }
        if (r.status == HttpStatusCode.NotFound) throw NessunItinerario()
        // Ktor non solleva da se' sugli stati d'errore (expectSuccess e' falso
        // per scelta): senza questo controllo un 500 finirebbe dentro la
        // deserializzazione e uscirebbe come errore di formato, che manda a
        // cercare il guasto nel posto sbagliato.
        if (!r.status.isSuccess()) throw Exception("piano: HTTP ${r.status.value}")
        return r.body()
    }
}
