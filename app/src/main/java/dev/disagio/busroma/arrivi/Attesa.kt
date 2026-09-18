package dev.disagio.busroma.arrivi

import java.time.Instant

/**
 * Minuti di attesa da un istante ISO-8601.
 *
 * SI CALCOLA QUI e non si usa il campo `minutes` della risposta, che è
 * un'istantanea al momento in cui il server ha risposto: mostrato tale e
 * quale invecchierebbe sullo schermo, e a una fermata la differenza fra "2
 * minuti" e "2 minuti di trenta secondi fa" è se corri o no.
 *
 * Il troncamento verso il basso è deliberato, come sul web: mancando 3 minuti
 * e 50 secondi si scrive "3", perché dire "4" a chi ne ha 3 e 50 lo fa
 * arrivare in ritardo. Meglio sottostimare l'attesa che sovrastimarla.
 *
 * `Instant.parse` richiede API 26, che è il nostro minimo: nessun
 * desugaring necessario.
 */
fun minutiDa(etaIso: String, adessoMs: Long): Int? {
    val istante = try {
        Instant.parse(etaIso)
    } catch (e: Exception) {
        // Un timestamp malformato non deve far cadere la lista: la riga
        // mostrera' un trattino.
        return null
    }
    val diff = istante.toEpochMilli() - adessoMs
    if (diff <= 0) return 0
    return (diff / 60_000L).toInt()
}
