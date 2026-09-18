package dev.disagio.busroma.dati

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Le forme che arrivano dalle API, scritte sul JSON vero.
 *
 * TUTTO CIÒ CHE PUÒ MANCARE È NULLABILE, e non per prudenza generica: sono i
 * casi osservati sul feed di produzione. `trip_id` è nullo quando l'arrivo
 * viene dalla tabella e non da un mezzo tracciato; `color` e `text_color` sono
 * nulli su tutte le linee di superficie e valorizzati solo sulle metropolitane;
 * `headsign` manca su alcune corse.
 *
 * LA REGOLA CHE PROTEGGE L'APP INSTALLATA (vedi PIANO.md §4): le API non sono
 * versionate, quindi il client deve tollerare campi che non conosce. Lo fa il
 * `Json { ignoreUnknownKeys = true }` in Api.kt. Qui la controparte è non
 * dichiarare obbligatorio nulla che il server possa smettere di mandare.
 */

@Serializable
data class RispostaArrivi(
    val stop: Fermata? = null,
    val arrivals: List<Arrivo> = emptyList(),
)

@Serializable
data class Fermata(
    @SerialName("stop_id") val stopId: String,
    val name: String,
    /** Il numero di palina: è come i romani identificano una fermata. */
    val code: String? = null,
)

@Serializable
data class Arrivo(
    @SerialName("route_id") val routeId: String,
    @SerialName("short_name") val shortName: String,
    val headsign: String? = null,
    @SerialName("direction_id") val directionId: Int? = null,
    /** Nullo quando l'arrivo è da tabella: non c'è una corsa tracciata. */
    @SerialName("trip_id") val tripId: String? = null,
    /** Istante ISO-8601 dell'arrivo previsto. È il dato da cui si calcola l'attesa. */
    @SerialName("eta_ts") val etaTs: String,
    /**
     * Minuti di attesa calcolati DAL SERVER al momento della risposta.
     *
     * Non si mostra: è un'istantanea e invecchia sullo schermo. L'attesa va
     * ricalcolata da `etaTs` a ogni battito dell'orologio, come fa il web con
     * `minutesUntil`. Il campo resta qui perché esiste nel JSON e serve a
     * ricordare che è una trappola, non un dato pronto.
     */
    val minutes: Int? = null,
    /**
     * Vero se il mezzo è tracciato davvero, falso se è solo l'orario previsto.
     * È la distinzione più importante dell'app, e in interfaccia è il verde
     * contro il grigio.
     */
    @SerialName("is_realtime") val isRealtime: Boolean = false,
    /** Scostamento in secondi dichiarato da ATAC. Non si mostra: vedi i ritardi sul web. */
    val delay: Int? = null,
    /** Colore ufficiale, presente solo sulle metropolitane. */
    val color: String? = null,
    @SerialName("text_color") val textColor: String? = null,
)

@Serializable
data class RispostaRicerca(
    val stops: List<FermataTrovata> = emptyList(),
)

@Serializable
data class FermataTrovata(
    @SerialName("stop_id") val stopId: String,
    val name: String,
    val code: String? = null,
    /** Le linee che fermano lì, per nome conosciuto ("117", "MEA"). */
    val routes: List<String> = emptyList(),
)

@Serializable
data class RispostaVicine(
    val stops: List<FermataVicina> = emptyList(),
)

@Serializable
data class FermataVicina(
    @SerialName("stop_id") val stopId: String,
    val name: String,
    val code: String? = null,
    /**
     * Distanza in metri calcolata dal server con PostGIS.
     *
     * È in LINEA D'ARIA, non a piedi. Sul web questa distinzione è costata la
     * giornata di lavoro più importante del progetto: su una fermata la linea
     * d'aria diceva 396 metri dove la strada reale era 4475, e il
     * pianificatore costruiva itinerari impossibili. Qui è accettabile perché
     * serve solo a ORDINARE le fermate vicine, non a dire quanto ci metti — e
     * per lo stesso motivo non va scritto "a 3 minuti a piedi".
     */
    @SerialName("distance_m") val distanzaM: Int? = null,
    val routes: List<String> = emptyList(),
)

@Serializable
data class RispostaArriviVicini(
    val arrivals: List<ArrivoVicino> = emptyList(),
)

/**
 * Un arrivo a una fermata vicina: e' un arrivo, non una fermata.
 *
 * Sul web la sezione "Qui intorno" mostra i BUS che stanno arrivando nelle
 * vicinanze, non l'elenco delle paline: e' la differenza fra "dove sono le
 * fermate" e "cosa posso prendere adesso", e la seconda e' la domanda che si
 * fa uno in strada.
 */
@Serializable
data class ArrivoVicino(
    @SerialName("route_id") val routeId: String,
    @SerialName("short_name") val shortName: String,
    val headsign: String? = null,
    @SerialName("direction_id") val directionId: Int? = null,
    @SerialName("eta_ts") val etaTs: String,
    /** Istantanea del server: per mostrare si ricalcola da etaTs. */
    val minutes: Int? = null,
    @SerialName("is_realtime") val isRealtime: Boolean = false,
    val delay: Int? = null,
    @SerialName("stop_id") val stopId: String,
    @SerialName("stop_name") val stopName: String,
    @SerialName("stop_code") val stopCode: String? = null,
    /** In linea d'aria: serve a ordinare, non a dire quanto ci metti a piedi. */
    @SerialName("distance_m") val distanzaM: Int? = null,
    val color: String? = null,
    @SerialName("text_color") val textColor: String? = null,
)
