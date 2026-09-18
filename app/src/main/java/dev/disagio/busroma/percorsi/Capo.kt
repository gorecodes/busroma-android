package dev.disagio.busroma.percorsi

import dev.disagio.busroma.dati.LuogoTrovato

/**
 * Un capo del viaggio: la posizione attuale, una fermata, o un luogo.
 *
 * Tre forme e non una sola con campi nullabili, perché il server accetta
 * ESATTAMENTE due modi di indicare un capo — uno `stopId`, oppure una coppia
 * di coordinate — e sono mutuamente esclusivi. Con una classe sola e quattro
 * campi opzionali si potrebbe costruire uno stato che l'API rifiuta; così no.
 *
 * La posizione attuale è separata dal luogo pur avendo gli stessi due campi:
 * non si chiama col nome di una via, si chiama "La mia posizione", e quella
 * differenza è tutta qui dentro invece di essere un flag da ricordarsi.
 */
sealed interface Capo {

    /** Come si chiama questo capo per chi legge. */
    val etichetta: String

    data class MiaPosizione(val lat: Double, val lon: Double) : Capo {
        override val etichetta get() = "La mia posizione"
    }

    data class Fermata(val stopId: String, val nome: String) : Capo {
        override val etichetta get() = nome
    }

    data class Luogo(val lat: Double, val lon: Double, val nome: String) : Capo {
        override val etichetta get() = nome
    }
}

/**
 * Da un risultato di ricerca al capo corrispondente, o null se il risultato
 * non è utilizzabile.
 *
 * Il null non è teorico: `/api/geocode` mette lo `stopId` sulle fermate e le
 * coordinate sui luoghi, e un risultato senza né l'uno né le altre non si può
 * dare al pianificatore. Preferisco scartarlo qui che mandare al server una
 * richiesta che tornerà 400.
 */
fun LuogoTrovato.aCapo(): Capo? = when {
    kind == "stop" && stopId != null -> Capo.Fermata(stopId, label)
    lat != null && lon != null -> Capo.Luogo(lat, lon, label)
    else -> null
}
