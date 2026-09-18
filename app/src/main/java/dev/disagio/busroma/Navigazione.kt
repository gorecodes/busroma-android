package dev.disagio.busroma

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.disagio.busroma.arrivi.SchermataArrivi
import dev.disagio.busroma.ricerca.SchermataRicerca
import kotlinx.serialization.Serializable

/**
 * Le destinazioni, come tipi e non come stringhe.
 *
 * Navigation Compose accetta rotte tipizzate serializzabili: l'identificativo
 * di fermata viaggia come proprietà invece che dentro un percorso da comporre
 * e ricomporre a mano. Le fermate romane hanno identificativi alfanumerici e
 * una di quelle stringhe costruite a mano è il modo classico di scoprire, tre
 * mesi dopo, che una palina con un carattere insolito rompe la navigazione.
 */
@Serializable
object Ricerca

@Serializable
data class Arrivi(val stopId: String)

/**
 * L'albero di navigazione.
 *
 * Il tasto indietro è quello di sistema e basta: non c'è un indietro disegnato
 * in cima. Su Android la gestualità di sistema è l'indietro, e aggiungerne un
 * secondo sarebbe rumore — sul web invece serviva, perché il browser non ha un
 * gesto affidabile dentro una PWA installata.
 */
@Composable
fun AppBusRoma() {
    val nav = rememberNavController()

    Scaffold { padding ->
        NavHost(
            navController = nav,
            startDestination = Ricerca,
            modifier = Modifier.padding(padding),
        ) {
            composable<Ricerca> {
                SchermataRicerca(
                    apriFermata = { stopId -> nav.navigate(Arrivi(stopId)) },
                )
            }
            composable<Arrivi> { voce ->
                val rotta: Arrivi = voce.toRoute()
                SchermataArrivi(stopId = rotta.stopId)
            }
        }
    }
}
