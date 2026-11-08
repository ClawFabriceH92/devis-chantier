package com.trucdecomptable.devischantier

import com.trucdecomptable.devischantier.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun comparaison_de_versions_segment_par_segment() {
        assertTrue(UpdateChecker.compareVersions("1.10.0", "1.9.0") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.0.0") == 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.0.1") < 0)
        assertTrue(UpdateChecker.compareVersions("2.0", "1.99.99") > 0)
    }

    @Test
    fun manifeste_version_plus_recente_propose_la_mise_a_jour() {
        val corps = """{"version":"1.2.0","notes":"correctifs","apk":"https://exemple/app.apk"}"""
        val resultat = UpdateChecker.parseManifest(corps, "1.0.0")
        assertTrue(resultat is UpdateChecker.CheckResult.Available)
        val disponible = resultat as UpdateChecker.CheckResult.Available
        assertEquals("1.2.0", disponible.info.version)
        assertEquals("https://exemple/app.apk", disponible.info.apkUrl)
    }

    @Test
    fun manifeste_meme_version_est_a_jour() {
        val corps = """{"version":"1.0.0","apk":"https://exemple/app.apk"}"""
        assertTrue(UpdateChecker.parseManifest(corps, "1.0.0") is UpdateChecker.CheckResult.UpToDate)
    }

    @Test
    fun manifeste_illisible_est_ignore() {
        assertNull(UpdateChecker.parseManifest("pas du json", "1.0.0"))
        assertNull(UpdateChecker.parseManifest("""{"notes":"sans version"}""", "1.0.0"))
    }

    @Test
    fun api_choisit_la_version_la_plus_haute_avec_un_apk() {
        val corps = """
            [
              {"tag_name":"v1.1.0","name":"Devis Chantier v1.1.0","draft":false,
               "assets":[{"name":"app-release.apk","browser_download_url":"https://exemple/1.1.apk"}]},
              {"tag_name":"v1.3.0","name":"Devis Chantier v1.3.0","draft":false,
               "assets":[{"name":"app-release.apk","browser_download_url":"https://exemple/1.3.apk"}]},
              {"tag_name":"v1.2.0","name":"v1.2.0","draft":true,
               "assets":[{"name":"app-release.apk","browser_download_url":"https://exemple/1.2.apk"}]}
            ]
        """.trimIndent()
        val resultat = UpdateChecker.parseApi(corps, "1.0.0")
        assertTrue(resultat is UpdateChecker.CheckResult.Available)
        assertEquals("1.3.0", (resultat as UpdateChecker.CheckResult.Available).info.version)
    }

    @Test
    fun api_ignore_une_release_sans_apk() {
        val corps = """[{"tag_name":"v9.9.9","draft":false,"assets":[{"name":"notes.txt","browser_download_url":"https://exemple/x"}]}]"""
        assertTrue(UpdateChecker.parseApi(corps, "1.0.0") is UpdateChecker.CheckResult.UpToDate)
    }

    @Test
    fun api_json_invalide_est_ignore() {
        assertNull(UpdateChecker.parseApi("<html>", "1.0.0"))
    }

    @Test
    fun extraction_de_version_depuis_un_titre() {
        assertEquals("1.20.1", UpdateChecker.extractVersion("Devis Chantier v1.20.1"))
        assertNull(UpdateChecker.extractVersion("latest"))
    }

    @Test
    fun raison_http_est_explicite() {
        assertTrue(UpdateChecker.httpReason(403, "0").contains("quota GitHub"))
        assertTrue(UpdateChecker.httpReason(404, null).contains("404"))
        assertEquals("HTTP 500", UpdateChecker.httpReason(500, null))
    }
}
