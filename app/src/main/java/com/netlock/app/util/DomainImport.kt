package com.netlock.app.util

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object DomainImport {

    /**
     * Normalizes a raw user-entered or imported string into a bare hostname:
     * strips scheme (http/https), path, port, "www.", surrounding whitespace/quotes.
     * Returns null if nothing usable is left.
     */
    fun normalize(raw: String): String? {
        var s = raw.trim().trim('"', '\'').lowercase()
        if (s.isEmpty() || s.startsWith("#")) return null

        s = s.removePrefix("https://").removePrefix("http://")
        // drop path/query
        s = s.substringBefore("/")
        // drop port
        s = s.substringBefore(":")
        // drop leading www.
        if (s.startsWith("www.")) s = s.removePrefix("www.")

        if (s.isEmpty() || !s.contains(".")) return null
        // very loose hostname sanity check
        if (!s.matches(Regex("^[a-z0-9.-]+$"))) return null

        return s
    }

    /**
     * Reads a .txt or .csv file's contents (one domain per line, or comma separated)
     * and returns a de-duplicated set of normalized domains.
     */
    fun parseFile(inputStream: InputStream): Set<String> {
        val results = LinkedHashSet<String>()
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.forEachLine { line ->
                line.split(",", ";", "\t").forEach { token ->
                    normalize(token)?.let { results.add(it) }
                }
            }
        }
        return results
    }
}
