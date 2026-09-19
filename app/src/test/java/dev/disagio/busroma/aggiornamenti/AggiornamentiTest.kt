package dev.disagio.busroma.aggiornamenti

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AggiornamentiTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ─────────────────────────────────────────────────────────────────────────
    // daMostrare
    // ─────────────────────────────────────────────────────────────────────────

    @Test fun `versione piu nuova viene mostrata`() {
        val remota = VersioneRemota(versionCode = 6, versionName = "0.2.0", apk = "busroma-0.2.0.apk")
        assertTrue(daMostrare(remota, codiceInstallato = 5, codiceIgnorato = null))
    }

    @Test fun `versione uguale non viene mostrata`() {
        val remota = VersioneRemota(versionCode = 5, versionName = "0.1.0", apk = "busroma-0.1.0.apk")
        assertFalse(daMostrare(remota, codiceInstallato = 5, codiceIgnorato = null))
    }

    @Test fun `versione piu vecchia non viene mostrata`() {
        val remota = VersioneRemota(versionCode = 4, versionName = "0.0.4", apk = "busroma-0.0.4.apk")
        assertFalse(daMostrare(remota, codiceInstallato = 5, codiceIgnorato = null))
    }

    @Test fun `versione nuova ignorata non viene mostrata`() {
        // L'utente ha già detto "non mostrarmi la 6": si rispetta.
        val remota = VersioneRemota(versionCode = 6, versionName = "0.2.0", apk = "busroma-0.2.0.apk")
        assertFalse(daMostrare(remota, codiceInstallato = 5, codiceIgnorato = 6))
    }

    @Test fun `ignorare la versione precedente non zittisce quella nuova`() {
        // L'utente aveva ignorato la 5, ma la 6 è più nuova e deve ricomparire.
        val remota = VersioneRemota(versionCode = 6, versionName = "0.2.0", apk = "busroma-0.2.0.apk")
        assertTrue(daMostrare(remota, codiceInstallato = 5, codiceIgnorato = 5))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deserializzazione di VersioneRemota
    // ─────────────────────────────────────────────────────────────────────────

    @Test fun `deserializza versione completa con note`() {
        val testo = """
            {
                "versionCode": 7,
                "versionName": "0.3.0",
                "apk": "busroma-0.3.0.apk",
                "note": "Miglioramenti vari"
            }
        """.trimIndent()
        val v = json.decodeFromString<VersioneRemota>(testo)
        assertEquals(7, v.versionCode)
        assertEquals("0.3.0", v.versionName)
        assertEquals("busroma-0.3.0.apk", v.apk)
        assertEquals("Miglioramenti vari", v.note)
    }

    @Test fun `deserializza versione senza note`() {
        val testo = """
            {
                "versionCode": 7,
                "versionName": "0.3.0",
                "apk": "busroma-0.3.0.apk"
            }
        """.trimIndent()
        val v = json.decodeFromString<VersioneRemota>(testo)
        assertEquals(7, v.versionCode)
        assertNull(v.note)
    }

    @Test fun `deserializza versione con campo sconosciuto in piu`() {
        // Il manifesto può crescere: campi futuri non devono rompere le versioni vecchie.
        val testo = """
            {
                "versionCode": 7,
                "versionName": "0.3.0",
                "apk": "busroma-0.3.0.apk",
                "campoFuturo": "ignorato",
                "altroAncora": 42
            }
        """.trimIndent()
        val v = json.decodeFromString<VersioneRemota>(testo)
        assertEquals(7, v.versionCode)
        assertEquals("busroma-0.3.0.apk", v.apk)
    }
}
