package dev.disagio.busroma.sveglie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CadenzaTest {

    // Istante di riferimento arbitrario, passato sempre da fuori.
    private val ORA = 1_000_000L

    // Vigilanza generica: scadenza lontanissima, nessun tentativo fallito,
    // ETA a 20 minuti da ORA.
    private fun vigilanza(
        etaMs: Long = ORA + 20 * 60_000L,
        scadenzaMs: Long = Long.MAX_VALUE,
        tentativiFalliti: Int = 0,
    ) = Vigilanza(
        tripId = "trip1",
        stopId = "stop1",
        shortName = "117",
        etaIso = Instant.ofEpochMilli(etaMs).toString(),
        scadenzaMs = scadenzaMs,
        tentativiFalliti = tentativiFalliti,
    )

    // ─────────────────────────────────────────────────────────────────────────
    // passoMs – confini della scala
    // ─────────────────────────────────────────────────────────────────────────

    @Test fun `passo oltre 30 min e' 10 min`() =
        assertEquals(10 * 60_000L, passoMs(30 * 60_000L + 1))

    @Test fun `passo a esattamente 30 min e' 5 min`() =
        assertEquals(5 * 60_000L, passoMs(30 * 60_000L))

    @Test fun `passo fra 15 e 30 min e' 5 min`() =
        assertEquals(5 * 60_000L, passoMs(20 * 60_000L))

    @Test fun `passo a esattamente 15 min e' 2 min`() =
        assertEquals(2 * 60_000L, passoMs(15 * 60_000L))

    @Test fun `passo fra 8 e 15 min e' 2 min`() =
        assertEquals(2 * 60_000L, passoMs(10 * 60_000L))

    @Test fun `passo a esattamente 8 min e' 60 s`() =
        assertEquals(60_000L, passoMs(8 * 60_000L))

    @Test fun `passo sotto 8 min e' 60 s`() =
        assertEquals(60_000L, passoMs(5 * 60_000L))

    // ─────────────────────────────────────────────────────────────────────────
    // decidi – scadenza
    // ─────────────────────────────────────────────────────────────────────────

    @Test fun `oltre la scadenza abbandona anche con eta fresco`() {
        val v = vigilanza(scadenzaMs = ORA - 1)
        val esito = decidi(v, etaMsFresco = ORA + 10 * 60_000L, adessoMs = ORA)
        assertEquals(Esito.Abbandona, esito)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // decidi – eta fresco
    // ─────────────────────────────────────────────────────────────────────────

    @Test fun `notifica dentro la finestra con eta fresco`() {
        val eta = ORA + 3 * 60_000L  // 3 minuti: dentro i 5.5 della finestra
        val n = decidi(vigilanza(), etaMsFresco = eta, adessoMs = ORA) as Esito.Notifica
        assertEquals(3, n.minuti)
        assertTrue(n.fresco)
    }

    @Test fun `abbandona quando il bus e' passato`() {
        // residuo = PASSATO_MS − 1: sotto la soglia del minuto trascorso
        val eta = ORA + PASSATO_MS - 1
        val esito = decidi(vigilanza(), etaMsFresco = eta, adessoMs = ORA)
        assertEquals(Esito.Abbandona, esito)
    }

    @Test fun `riprogrammazione sceglie adesso+passo quando e' il minore`() {
        // residuo = 20 min → passo = 5 min
        // adesso + passo = ORA + 5 min  (minore)
        // eta   − 5 min  = ORA + 15 min
        val eta = ORA + 20 * 60_000L
        val r = decidi(vigilanza(etaMs = eta), etaMsFresco = eta, adessoMs = ORA) as Esito.Ricontrolla
        assertEquals(ORA + 5 * 60_000L, r.istanteMs)
    }

    @Test fun `riprogrammazione sceglie eta-5min quando e' il minore`() {
        // residuo = 340_000 ms (5 m 40 s, appena sopra la soglia 5m30s)
        // passo = 60_000 ms  (< 8 min)
        // eta   − 5 min  = ORA + 40_000 ms  (minore)
        // adesso + passo = ORA + 60_000 ms
        val eta = ORA + 340_000L
        val r = decidi(vigilanza(etaMs = eta), etaMsFresco = eta, adessoMs = ORA) as Esito.Ricontrolla
        assertEquals(ORA + 40_000L, r.istanteMs)
    }

    @Test fun `riprogrammazione non e' mai prima di MINIMO_MS`() {
        val eta = ORA + 20 * 60_000L
        val r = decidi(vigilanza(etaMs = eta), etaMsFresco = eta, adessoMs = ORA) as Esito.Ricontrolla
        assertTrue(r.istanteMs >= ORA + MINIMO_MS)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // decidi – tentativi falliti
    // ─────────────────────────────────────────────────────────────────────────

    @Test fun `primo tentativo fallito programa il retry`() {
        val r = decidi(vigilanza(tentativiFalliti = 0), etaMsFresco = null, adessoMs = ORA) as Esito.Ricontrolla
        assertEquals(1, r.vigilanza.tentativiFalliti)
        assertEquals(ORA + 60_000L, r.istanteMs)
    }

    @Test fun `secondo tentativo fallito programa il terzo`() {
        val r = decidi(vigilanza(tentativiFalliti = 1), etaMsFresco = null, adessoMs = ORA) as Esito.Ricontrolla
        assertEquals(2, r.vigilanza.tentativiFalliti)
        assertEquals(ORA + 60_000L, r.istanteMs)
    }

    @Test fun `tre tentativi falliti notificano col dato non fresco`() {
        // Al terzo fallimento l'ETA noto è dentro la finestra: si avvisa dichiarando non fresco.
        val etaMs = ORA + 3 * 60_000L
        val v = vigilanza(etaMs = etaMs, tentativiFalliti = 2)
        val n = decidi(v, etaMsFresco = null, adessoMs = ORA) as Esito.Notifica
        assertEquals(3, n.minuti)
        assertFalse(n.fresco)
    }

    @Test fun `tre tentativi falliti con eta fuori finestra abbandonano`() {
        val etaMs = ORA + 30 * 60_000L  // troppo lontano per notificare
        val v = vigilanza(etaMs = etaMs, tentativiFalliti = 2)
        val esito = decidi(v, etaMsFresco = null, adessoMs = ORA)
        assertEquals(Esito.Abbandona, esito)
    }

    @Test fun `eta illeggibile dopo tentativi esauriti abbandona`() {
        val v = Vigilanza(
            tripId = "trip1",
            stopId = "stop1",
            shortName = "117",
            etaIso = "non-un-timestamp",
            scadenzaMs = Long.MAX_VALUE,
            tentativiFalliti = 2,
        )
        val esito = decidi(v, etaMsFresco = null, adessoMs = ORA)
        assertEquals(Esito.Abbandona, esito)
    }
}
