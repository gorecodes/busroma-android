package dev.disagio.busroma.sveglie

import java.time.Instant

/**
 * Cosa fare a un risveglio.
 *
 * Tre esiti e non due, perché "chiudi la vigilanza" e "ricontrolla più tardi"
 * sono decisioni diverse e l'unica cosa che le distingue è questo tipo: il
 * ricevitore non deve ragionare, deve eseguire.
 */
sealed interface Esito {
    /**
     * Si notifica adesso.
     *
     * [fresco] è falso quando l'ETA viene dall'ultimo controllo riuscito e non
     * da una risposta di adesso: la notifica lo dichiara, come fa la schermata
     * degli arrivi coi dati vecchi.
     */
    data class Notifica(val minuti: Int, val fresco: Boolean) : Esito

    /** Si riprogramma la sveglia a [istanteMs] e si salva [vigilanza]. */
    data class Ricontrolla(val istanteMs: Long, val vigilanza: Vigilanza) : Esito

    /** Si chiude tutto, in silenzio. */
    object Abbandona : Esito
}

/** Si notifica da qui in giù: mezzo minuto più dei 5 del server, per lo scarto di consegna. */
const val SOGLIA_NOTIFICA_MS = 5 * 60_000L + 30_000L

/** Sotto questo residuo il bus è passato: non si notifica più. */
const val PASSATO_MS = -60_000L

/** Mai due controlli più vicini di così. */
const val MINIMO_MS = 30_000L

/** Ritentativi a rete assente, poi si decide col dato vecchio. */
const val MAX_TENTATIVI = 3

/**
 * Ogni quanto ricontrollare, dato il residuo.
 *
 * LA SCALA SI STRINGE AVVICINANDOSI, e la ragione non è il risparmio: è che un
 * ETA può ANTICIPARE. Una sveglia sola, programmata sull'ETA visto al tocco,
 * suonerebbe a bus già passato se il bus guadagna dieci minuti. Controllando a
 * passi via via più corti, l'errore sull'istante della notifica non supera mai
 * un passo, e all'ultimo minuto il passo è un minuto.
 *
 * Vedi la tabella in PIANO_SVEGLIA.md: 10 min oltre i 30, 5 min fra 15 e 30,
 * 2 min fra 8 e 15, 60 s sotto gli 8.
 */
fun passoMs(residuoMs: Long): Long = when {
    residuoMs > 30 * 60_000L -> 10 * 60_000L
    residuoMs > 15 * 60_000L ->  5 * 60_000L
    residuoMs >  8 * 60_000L ->  2 * 60_000L
    else                      ->      60_000L
}

/**
 * La decisione di un risveglio, senza toccare niente: è tutta la logica del
 * meccanismo, e sta in una funzione pura perché è l'unica parte che si può
 * provare davvero con un test.
 *
 * @param v la vigilanza come sta nel deposito.
 * @param etaMsFresco l'ETA appena letto dal server, o `null` se il controllo è
 *   andato male (rete assente, oppure corsa introvabile su entrambi gli
 *   endpoint).
 * @param adessoMs l'orologio, passato da fuori per poter essere finto nei test.
 */
fun decidi(v: Vigilanza, etaMsFresco: Long?, adessoMs: Long): Esito {
    // La scadenza ha priorità assoluta: una corsa cancellata non deve
    // tenere sveglio il telefono per sempre.
    if (adessoMs > v.scadenzaMs) return Esito.Abbandona

    if (etaMsFresco != null) {
        val residuo = etaMsFresco - adessoMs
        if (residuo < PASSATO_MS) return Esito.Abbandona
        if (residuo <= SOGLIA_NOTIFICA_MS) {
            return Esito.Notifica(
                minuti = (maxOf(0L, residuo) / 60_000L).toInt(),
                fresco = true,
            )
        }
        // Prossimo controllo: il minore fra eta−5min e adesso+passo,
        // mai meno di MINIMO_MS da adesso.
        val prossimo = maxOf(
            adessoMs + MINIMO_MS,
            minOf(etaMsFresco - 5 * 60_000L, adessoMs + passoMs(residuo)),
        )
        return Esito.Ricontrolla(
            istanteMs = prossimo,
            vigilanza = v.copy(
                etaIso = Instant.ofEpochMilli(etaMsFresco).toString(),
                tentativiFalliti = 0,
            ),
        )
    }

    // Controllo fallito (rete assente o corsa introvabile): i due casi
    // arrivano qui indistinguibili. Si riprova fino a MAX_TENTATIVI volte.
    val tentativi = v.tentativiFalliti + 1
    if (tentativi < MAX_TENTATIVI) {
        return Esito.Ricontrolla(adessoMs + 60_000L, v.copy(tentativiFalliti = tentativi))
    }

    // Tentativi esauriti: si decide con l'ETA noto all'ultimo controllo riuscito.
    val etaNotoMs = try {
        Instant.parse(v.etaIso).toEpochMilli()
    } catch (e: Exception) {
        return Esito.Abbandona
    }
    val residuoNoto = etaNotoMs - adessoMs
    return if (residuoNoto in PASSATO_MS..SOGLIA_NOTIFICA_MS) {
        Esito.Notifica(
            minuti = (maxOf(0L, residuoNoto) / 60_000L).toInt(),
            fresco = false,
        )
    } else {
        Esito.Abbandona
    }
}
