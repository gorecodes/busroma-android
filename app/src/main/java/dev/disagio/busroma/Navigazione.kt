package dev.disagio.busroma

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.disagio.busroma.arrivi.SchermataArrivi
import dev.disagio.busroma.avvisi.SchermataAvvisi
import dev.disagio.busroma.corsa.SchermataCorsa
import dev.disagio.busroma.linea.SchermataLinea
import dev.disagio.busroma.informazioni.SchermataInformazioni
import dev.disagio.busroma.percorsi.SchermataPercorsi
import dev.disagio.busroma.preferiti.SchermataPreferiti
import dev.disagio.busroma.ritardi.SchermataRitardi
import dev.disagio.busroma.ricerca.SchermataRicerca
import dev.disagio.busroma.ui.NavigazioneBasso
import dev.disagio.busroma.ui.Sezione
import dev.disagio.busroma.ui.theme.LocalPalette
import kotlinx.serialization.Serializable

/**
 * Le destinazioni, come tipi e non come stringhe.
 *
 * Navigation Compose accetta rotte tipizzate serializzabili: l'identificativo
 * di fermata viaggia come proprietà invece che dentro un percorso da
 * comporre a mano. Le fermate romane hanno identificativi alfanumerici, e una
 * di quelle stringhe costruite a mano è il modo classico di scoprire tre mesi
 * dopo che una palina con un carattere insolito rompe la navigazione.
 */
@Serializable
object Ricerca

@Serializable
object Preferiti

@Serializable
data class Arrivi(val stopId: String)

@Serializable
data class LineaRotta(val routeId: String, val verso: Int? = null)

@Serializable
object Percorsi

@Serializable
object Ritardi

@Serializable
object Informazioni

@Serializable
object Avvisi

@Serializable
data class Corsa(val tripId: String)

/**
 * L'albero di navigazione, con la barra in basso.
 *
 * LA BARRA COMPARE SOLO SULLE DESTINAZIONI PRINCIPALI. Sulla schermata di una
 * fermata si è scesi in profondità, e da lì l'uscita è l'indietro di
 * sistema: tenere la barra darebbe due modi di andarsene e nessuno dei due
 * chiaro.
 *
 * Il tasto indietro è quello di sistema e basta, nessun indietro disegnato in
 * cima: su Android la gestualità di sistema è l'indietro, e aggiungerne un
 * secondo sarebbe rumore. Sul web invece serviva, perché dentro una PWA
 * installata il browser non offre un gesto affidabile.
 */
@Composable
fun AppBusRoma(
    fermataDaAprire: String? = null,
    onFermataAperta: () -> Unit = {},
) {
    val nav = rememberNavController()
    val voce by nav.currentBackStackEntryAsState()
    val destinazione = voce?.destination

    val sezione: Sezione? = when {
        destinazione?.hasRoute<Preferiti>() == true -> Sezione.Preferiti
        destinazione?.hasRoute<Percorsi>() == true -> Sezione.Percorsi
        destinazione?.hasRoute<Ritardi>() == true -> Sezione.Ritardi
        destinazione?.hasRoute<Ricerca>() == true -> Sezione.Fermate
        else -> null
    }

    // Navigazione da notifica: una volta sola per valore, non a ogni
    // ricomposizione. La chiave cambia quando arriva un nuovo stop_id; il
    // reset via onFermataAperta() fa sì che lo stesso stop_id in una seconda
    // notifica successiva navighi di nuovo (il null intermedio cambia la chiave).
    LaunchedEffect(fermataDaAprire) {
        if (fermataDaAprire != null) {
            val entry = nav.currentBackStackEntry
            val giaLi = entry?.destination?.hasRoute<Arrivi>() == true &&
                entry.toRoute<Arrivi>().stopId == fermataDaAprire
            if (!giaLi) {
                nav.navigate(Arrivi(fermataDaAprire))
            }
            onFermataAperta()
        }
    }

    // Lo sfondo va messo sulla RADICE e non dentro le schermate: con il disegno
    // a tutto schermo l'area della barra di stato resta dipinta con lo sfondo
    // della finestra, che e' chiaro, e in tema scuro compariva una fascia
    // bianca in cima. Lo Scaffold lo faceva da se': togliendolo, va rifatto.
    Column(Modifier.fillMaxSize().background(LocalPalette.current.neutral100)) {
        NavHost(
            navController = nav,
            startDestination = Ricerca,
            // statusBarsPadding QUI e non uno Scaffold intorno: sostituendo lo
            // Scaffold con questa Column avevo perso la spaziatura in alto e i
            // titoli finivano sotto le icone della barra di stato. In basso
            // ci pensa NavigazioneBasso con navigationBarsPadding.
            modifier = Modifier.weight(1f).statusBarsPadding(),
        ) {
            composable<Ricerca> {
                SchermataRicerca(
                    apriFermata = { stopId -> nav.navigate(Arrivi(stopId)) },
                    apriLinea = { routeId, verso -> nav.navigate(LineaRotta(routeId, verso)) },
                    apriPreferiti = { nav.navigate(Preferiti) },
                    apriAvvisi = { nav.navigate(Avvisi) },
                    apriInformazioni = { nav.navigate(Informazioni) },
                )
            }
            composable<Preferiti> {
                SchermataPreferiti(
                    apri = { stopId -> nav.navigate(Arrivi(stopId)) },
                    apriAvvisi = { nav.navigate(Avvisi) },
                    apriInformazioni = { nav.navigate(Informazioni) },
                )
            }
            composable<Percorsi> {
                SchermataPercorsi(
                    apriFermata = { stopId -> nav.navigate(Arrivi(stopId)) },
                    apriAvvisi = { nav.navigate(Avvisi) },
                    apriInformazioni = { nav.navigate(Informazioni) },
                )
            }
            composable<Ritardi> {
                SchermataRitardi(
                    apriAvvisi = { nav.navigate(Avvisi) },
                    apriInformazioni = { nav.navigate(Informazioni) },
                )
            }
            composable<Informazioni> {
                SchermataInformazioni(apriAvvisi = { nav.navigate(Avvisi) })
            }
            composable<Avvisi> { SchermataAvvisi() }
            composable<Corsa> { v ->
                val rotta: Corsa = v.toRoute()
                SchermataCorsa(
                    tripId = rotta.tripId,
                    apriFermata = { stopId -> nav.navigate(Arrivi(stopId)) },
                    apriAvvisi = { nav.navigate(Avvisi) },
                )
            }
            composable<Arrivi> { v ->
                val rotta: Arrivi = v.toRoute()
                SchermataArrivi(
                    stopId = rotta.stopId,
                    apriCorsa = { tripId -> nav.navigate(Corsa(tripId)) },
                    apriLinea = { routeId, verso -> nav.navigate(LineaRotta(routeId, verso)) },
                    apriAvvisi = { nav.navigate(Avvisi) },
                )
            }
            composable<LineaRotta> { v ->
                val rotta: LineaRotta = v.toRoute()
                SchermataLinea(
                    routeId = rotta.routeId,
                    versoIniziale = rotta.verso,
                    apriFermata = { stopId -> nav.navigate(Arrivi(stopId)) },
                    apriCorsa = { tripId -> nav.navigate(Corsa(tripId)) },
                    apriAvvisi = { nav.navigate(Avvisi) },
                )
            }
        }

        // SEMPRE VISIBILE, su ogni schermata, come sul web.
        //
        // Prima la nascondevo sulle schermate di dettaglio, ragionando che su
        // Android l'uscita e' l'indietro di sistema e due modi di andarsene
        // sarebbero rumore. Sbagliato per due motivi: rompe la parita' col
        // web, e soprattutto toglie una cosa utile - dalla pagina di una
        // fermata si salta ai Preferiti senza dover prima tornare indietro.
        // Sulle schermate di dettaglio nessuna voce risulta accesa, che e'
        // esattamente quello che fa il web.
        NavigazioneBasso(attiva = sezione) { scelta ->
            // popUpTo(Ricerca) NON inclusivo, e launchSingleTop: si torna alla
            // schermata Ricerca che esiste gia' invece di ricrearla, quindi il
            // testo cercato e i risultati sopravvivono. Con `inclusive = true`
            // la si distruggeva e si ripartiva da un campo vuoto - la stessa
            // perdita di stato di cui l'utente si era lamentato sul web.
            //
            // Vale anche per Preferiti: cosi' la pila resta bassa
            // (Ricerca -> Preferiti) invece di impilarsi sopra la fermata da
            // cui si e' partiti.
            when (scelta) {
                Sezione.Fermate -> nav.navigate(Ricerca) {
                    popUpTo(Ricerca)
                    launchSingleTop = true
                }
                Sezione.Percorsi -> nav.navigate(Percorsi) {
                    popUpTo(Ricerca)
                    launchSingleTop = true
                }
                Sezione.Ritardi -> nav.navigate(Ritardi) {
                    popUpTo(Ricerca)
                    launchSingleTop = true
                }
                Sezione.Preferiti -> nav.navigate(Preferiti) {
                    popUpTo(Ricerca)
                    launchSingleTop = true
                }
            }
        }
    }
}
