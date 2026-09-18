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

@Serializable
data class RispostaLinee(
    val routes: List<Linea> = emptyList(),
)

@Serializable
data class Linea(
    @SerialName("route_id") val routeId: String,
    @SerialName("short_name") val shortName: String,
    /**
     * ATTENZIONE: e' la STRINGA VUOTA, non null, su 360 delle 434 linee di
     * Roma. Sul web `longName ?? etichetta` non la intercettava e il titolo
     * restava vuoto: serve un controllo sul contenuto. Vedi `nomeLinea`.
     */
    @SerialName("long_name") val longName: String? = null,
    /** route_type GTFS: 0 tram, 1 metro, 2 treno, 3 bus, 11 filobus. */
    val type: Int = 3,
    val color: String? = null,
    @SerialName("text_color") val textColor: String? = null,
)

/** route_type GTFS -> etichetta, come sul web. */
fun etichettaTipo(type: Int): String = when (type) {
    0 -> "Tram"
    1 -> "Metro"
    2 -> "Treno"
    3 -> "Bus"
    4 -> "Traghetto"
    5, 7 -> "Funicolare"
    11 -> "Filobus"
    else -> "Linea"
}

/**
 * Nome da mostrare per una linea: il nome lungo se c'e' qualcosa dentro,
 * altrimenti il tipo. Il controllo e' sul CONTENUTO e non sulla nullita',
 * perche' la gran parte delle linee romane ha long_name uguale a "".
 */
fun nomeLinea(longName: String?, type: Int): String =
    longName?.trim()?.takeIf { it.isNotEmpty() } ?: etichettaTipo(type)

@Serializable
data class RispostaLinea(
    val route: Linea? = null,
    val directions: List<Verso> = emptyList(),
)

@Serializable
data class Verso(
    @SerialName("direction_id") val directionId: Int,
    val headsign: String? = null,
)

@Serializable
data class RispostaFermateLinea(
    val stops: List<FermataLinea> = emptyList(),
    /**
     * Il tracciato della linea come GeoJSON LineString, per la mappa.
     *
     * Nullabile perche' non tutte le linee hanno una shape nel GTFS, e
     * perche' l'app funzionava senza mappa: un client vecchio che non conosce
     * questo campo lo ignora, e uno nuovo su una linea senza tracciato mostra
     * solo le fermate. Sono 700 punti per la 982, quindi si legge una volta
     * al cambio di verso e non a ogni disegno.
     */
    val shape: Tracciato? = null,
)

/**
 * Un GeoJSON LineString ridotto all'osso.
 *
 * `coordinates` e' una lista di coppie [lon, lat] - in QUEST'ORDINE, che e'
 * l'ordine di GeoJSON e l'inverso di come si dicono le coordinate a voce.
 * Sbagliarlo non da' errore, mette la linea in Somalia.
 */
@Serializable
data class Tracciato(
    val type: String = "LineString",
    val coordinates: List<List<Double>> = emptyList(),
)

@Serializable
data class FermataLinea(
    @SerialName("stop_id") val stopId: String,
    val name: String,
    val code: String? = null,
    @SerialName("stop_sequence") val sequenza: Int,
    val lon: Double,
    val lat: Double,
)

@Serializable
data class RispostaMezzi(
    val vehicles: List<Mezzo> = emptyList(),
)

@Serializable
data class Mezzo(
    @SerialName("vehicle_id") val vehicleId: String,
    val lat: Double,
    val lon: Double,
    val bearing: Double? = null,
    @SerialName("trip_id") val tripId: String? = null,
    /**
     * Spesso NULLO nel feed ATAC, anche con il mezzo localizzato.
     * Per questo la posizione sulla lista si calcola dalle coordinate e non da
     * qui: e' la stessa scelta del web, dove `liveByStop` cerca la fermata piu'
     * vicina al GPS del mezzo invece di fidarsi di questo campo.
     */
    @SerialName("next_stop_id") val nextStopId: String? = null,
)

@Serializable
data class StatoFeedDto(
    @SerialName("last_fetch") val ultimoAggiornamento: String? = null,
    /** Secondi da quando ATAC ha aggiornato: sopra 90 qualcosa non va. */
    @SerialName("stale_s") val stantioS: Int? = null,
)

@Serializable
data class RispostaAvvisi(
    val avvisi: List<Avviso> = emptyList(),
    val urgenti: Int = 0,
)

/**
 * Un avviso di servizio, GIA' NORMALIZZATO DAL SERVER.
 *
 * Il client non ripete nessuna delle scelte fatte lato web: la distinzione fra
 * urgente e strutturale (durata sotto i due giorni), la pulizia delle
 * virgolette Windows e delle entita' HTML, lo scarto degli avvisi scaduti,
 * l'intersezione delle linee con la fermata. Tutto questo vive in
 * /api/alerts, e il client riceve testo pronto da mostrare.
 *
 * E' il "client stupido" del piano applicato al caso piu' delicato: se un
 * domani la classificazione cambia, l'app installata non va aggiornata.
 */
@Serializable
data class Avviso(
    val id: String,
    val titolo: String,
    val dettaglio: String? = null,
    val effetto: String,
    val causa: String? = null,
    /** Dura un giorno: e' la notizia di oggi, non un cantiere di sei mesi. */
    val urgente: Boolean = false,
    val quando: String? = null,
    val linee: List<String> = emptyList(),
    val lineeQui: List<String> = emptyList(),
    val toccaQui: Boolean = false,
)

@Serializable
data class RispostaCorsa(
    val route: Linea? = null,
    /**
     * Nullo quando la corsa e' in tabella ma nessun mezzo la sta segnalando.
     * Sul web questo caso ha un nome: "mezzo fantasma". Dirlo e' meglio che
     * mostrare una pagina identica a quella di un mezzo tracciato.
     */
    val vehicle: MezzoCorsa? = null,
    val stops: List<FermataCorsa> = emptyList(),
    val headsign: String? = null,
    @SerialName("direction_id") val directionId: Int? = null,
)

@Serializable
data class MezzoCorsa(
    @SerialName("vehicle_id") val vehicleId: String,
    val lat: Double,
    val lon: Double,
    val bearing: Double? = null,
    /** Quando ATAC ha visto il mezzo: serve a dire se il dato e' fresco. */
    val ts: String? = null,
)

@Serializable
data class FermataCorsa(
    @SerialName("stop_id") val stopId: String,
    val name: String,
    val code: String? = null,
    @SerialName("stop_sequence") val sequenza: Int,
    /** Nullo su qualche fermata: il feed non sempre le copre tutte. */
    @SerialName("eta_ts") val etaTs: String? = null,
    val delay: Int? = null,
    val lon: Double = 0.0,
    val lat: Double = 0.0,
)

// ---------------------------------------------------------------------------
// PIANIFICATORE
//
// Il calcolo NON sta qui. L'algoritmo (una Connection Scan) gira sul server in
// /api/plan, insieme alle sue seicento righe di politiche: raggio di accesso,
// finestra di campionamento, scarto delle opzioni dominate. L'app manda due
// capi e un orario e disegna quello che torna. Portare il router sul telefono
// avrebbe significato scaricare l'intero orario di Roma e mantenere due
// implementazioni destinate a divergere.
// ---------------------------------------------------------------------------

/**
 * Una fermata dentro un itinerario. Attenzione: qui il campo è `stopId` in
 * cammello, non `stop_id`, perché /api/plan compone la risposta a mano invece
 * di rigirare le righe del database.
 */
@Serializable
data class FermataItinerario(
    val stopId: String,
    val name: String,
    val code: String? = null,
)

/**
 * Un pezzo di viaggio: o si cammina, o si sta su un mezzo.
 *
 * Gerarchia sigillata e non un'unica classe con tutto nullabile: le due forme
 * hanno campi diversi per davvero — una tratta a piedi non ha una linea, una
 * in mezzo ha sempre due fermate — e tenerle separate fa sì che la schermata
 * non possa dimenticare un caso.
 *
 * Il discriminatore è il campo `kind`, configurato in Api.kt: si usa
 * l'impostazione del costruttore Json invece dell'annotazione
 * @JsonClassDiscriminator, che è ancora sperimentale.
 */
@Serializable
sealed interface Tratta

@Serializable
@SerialName("walk")
data class TrattaAPiedi(
    val from: FermataItinerario? = null,
    val to: FermataItinerario? = null,
    val minutes: Int,
    /** Presente solo sull'opzione "tutto a piedi". */
    val meters: Int? = null,
    val departAt: String? = null,
    val arriveAt: String? = null,
) : Tratta

@Serializable
@SerialName("ride")
data class TrattaInMezzo(
    val tripId: String,
    val shortName: String,
    val color: String? = null,
    val textColor: String? = null,
    val headsign: String? = null,
    val from: FermataItinerario,
    val to: FermataItinerario,
    val departAt: String,
    val arriveAt: String,
    val minutes: Int,
) : Tratta

@Serializable
data class OpzioneItinerario(
    val departAt: String,
    val arriveAt: String,
    /**
     * Da quando esci a quando arrivi: cammino, viaggio e cambi, MA NON
     * l'attesa iniziale. Quella dipende solo da quando ti trovi a uscire, e
     * conteggiarla fa sembrare scarso un percorso ottimo il cui autobus è
     * appena passato.
     */
    val durationMin: Int,
    /** Gli orari sono di UNA corsa a titolo d'esempio, non "la" partenza. */
    val esempio: Boolean = true,
    val walkMin: Int = 0,
    val rides: Int = 0,
    /** Le linee in ordine: serve a riconoscere il percorso a colpo d'occhio. */
    val lines: List<String> = emptyList(),
    val legs: List<Tratta> = emptyList(),
)

@Serializable
data class OpzioneAPiedi(val minutes: Int, val meters: Int)

@Serializable
data class Piano(
    val options: List<OpzioneItinerario> = emptyList(),
    /** Presente solo se camminare è un'alternativa sensata da confrontare. */
    val walkOption: OpzioneAPiedi? = null,
)

/**
 * Un risultato della ricerca unificata dei capi: fermate del GTFS e luoghi di
 * OpenStreetMap nello stesso elenco, perché per chi cerca sono la stessa cosa.
 *
 * Una fermata ha `stopId` e non le coordinate; un luogo ha le coordinate e non
 * lo `stopId`. Chi legge deve controllare quale dei due c'è.
 */
@Serializable
data class LuogoTrovato(
    /** "stop", "street", "address" o "poi". */
    val kind: String,
    val id: String,
    val label: String,
    val detail: String? = null,
    val stopId: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
data class RispostaGeocodifica(val results: List<LuogoTrovato> = emptyList())

/**
 * Un passaggio di UNA linea a UNA fermata, per il pannello che si apre
 * toccando una fermata nell'elenco del percorso.
 *
 * Non riusa `Arrivo`: quello risponde a "cosa passa da questa fermata" e
 * porta la linea con sé (short_name, colori, verso), qui la linea si sa già
 * ed è l'orario la cosa nuova. Riusarlo avrebbe voluto dire dichiarare
 * opzionali metà dei suoi campi, e a quel punto non descrive più niente.
 */
@Serializable
data class PassaggioLinea(
    @SerialName("trip_id") val tripId: String? = null,
    val headsign: String? = null,
    @SerialName("eta_ts") val etaTs: String,
    val minutes: Int? = null,
    @SerialName("is_realtime") val isRealtime: Boolean = false,
    val delay: Int? = null,
)

@Serializable
data class RispostaPassaggiLinea(
    val arrivals: List<PassaggioLinea> = emptyList(),
)

// ---------------------------------------------------------------------------
// RITARDI
//
// Il conteggio lo fa il server, una riga per corsa per fascia oraria. Qui
// arrivano solo le linee che hanno superato la soglia di campione.
// ---------------------------------------------------------------------------

@Serializable
data class PeriodoRitardi(
    val dal: String? = null,
    val al: String? = null,
    /** Corse osservate in tutto: e' il campione, e va dichiarato. */
    val corse: Int = 0,
    val ore: Int = 0,
)

@Serializable
data class LineaRitardo(
    @SerialName("short_name") val shortName: String,
    val color: String? = null,
    @SerialName("text_color") val textColor: String? = null,
    val corse: Int = 0,
    /** Scostamento medio in secondi: positivo e' ritardo, negativo anticipo. */
    @SerialName("media_s") val mediaS: Int = 0,
    /** Percentuale di corse oltre i 5 minuti. */
    @SerialName("perc_ritardo") val percRitardo: Int = 0,
    @SerialName("peggiore_s") val peggioreS: Int = 0,
)

@Serializable
data class RispostaRitardi(
    val periodo: PeriodoRitardi = PeriodoRitardi(),
    /** Soglia di corse sotto la quale una linea non compare. */
    val minCorse: Int = 0,
    val linee: List<LineaRitardo> = emptyList(),
)
