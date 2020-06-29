package com.openlattice.rehearsal

import com.google.common.io.Resources
import org.apache.commons.io.IOUtils
import org.junit.Assert
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */
class RehearsalBootstrap {
    companion object {
        private val rehearsal = Rehearsal()
        init {
            rehearsal.start("local")
        }
    }

    @Test
    fun testPing() {
        val url = URL("http://localhost:8079/admin/ping")
        val maybePong = with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"
            IOUtils.toString(inputStream.bufferedReader())
        }

        Assert.assertEquals("pong\n", maybePong)
    }
}