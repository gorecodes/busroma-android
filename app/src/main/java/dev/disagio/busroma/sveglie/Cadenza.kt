package dev.disagio.busroma.sveglie

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
fun passoMs(residuoMs: Long): Long = TODO()

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
fun decidi(v: Vigilanza, etaMsFresco: Long?, adessoMs: Long): Esito = TODO()
