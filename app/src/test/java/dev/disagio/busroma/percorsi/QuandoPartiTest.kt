package dev.disagio.busroma.percorsi

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class QuandoPartiTest {

    private val ROMA = ZoneId.of("Europe/Rome")

    private fun romaMs(anno: Int, mese: Int, giorno: Int, ore: Int, minuti: Int): Long =
        LocalDateTime.of(anno, mese, giorno, ore, minuti)
            .atZone(ROMA)
            .toInstant()
            .toEpochMilli()

    private fun mezzanotteUtcMs(anno: Int, mese: Int, giorno: Int): Long =
        LocalDateTime.of(anno, mese, giorno, 0, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

    // Ore 00:30 di Roma in estate (CEST = UTC+2): la data UTC è ancora il 14,
    // ma il selettore deve aprirsi sul 15 luglio, che è la data romana.
    @Test fun `00h30 ora legale torna mezzanotte UTC del giorno romano`() {
        val ms = romaMs(2024, 7, 15, 0, 30)
        assertEquals(mezzanotteUtcMs(2024, 7, 15), mezzanotteUtcDi(ms))
    }

    // Ore 12:00 di Roma in estate: nessuna ambiguità, il giorno è lo stesso
    // sia in UTC sia a Roma.
    @Test fun `12h00 ora legale torna mezzanotte UTC del giorno romano`() {
        val ms = romaMs(2024, 7, 15, 12, 0)
        assertEquals(mezzanotteUtcMs(2024, 7, 15), mezzanotteUtcDi(ms))
    }

    // Ore 00:30 di Roma in inverno (CET = UTC+1): la data UTC è ancora il 14,
    // ma il selettore deve aprirsi sul 15 gennaio, che è la data romana.
    @Test fun `00h30 ora solare torna mezzanotte UTC del giorno romano`() {
        val ms = romaMs(2024, 1, 15, 0, 30)
        assertEquals(mezzanotteUtcMs(2024, 1, 15), mezzanotteUtcDi(ms))
    }
}
