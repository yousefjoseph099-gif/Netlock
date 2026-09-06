package com.netlock.app.util

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

object AppsHelper {

    // Well-known browser packages get bumped to the top of the picker list by default.
    val KNOWN_BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "org.mozilla.focus",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.brave.browser",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.UCMobile.intl",
        "com.kiwibrowser.browser",
        "com.google.android.apps.chrome"
    )

    /**
     * Lists apps capable of handling a web link (i.e. browsers), using package-visibility
     * friendly queryIntentActivities rather than requesting QUERY_ALL_PACKAGES.
     */
    fun listBrowserCandidates(pm: PackageManager): List<InstalledAppInfo> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        val seen = LinkedHashMap<String, InstalledAppInfo>()
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (seen.containsKey(pkg)) continue
            val label = info.loadLabel(pm)?.toString() ?: pkg
            seen[pkg] = InstalledAppInfo(pkg, label)
        }

        return seen.values.sortedWith(
            compareByDescending<InstalledAppInfo> { KNOWN_BROWSER_PACKAGES.contains(it.packageName) }
                .thenBy { it.label.lowercase() }
        )
    }
}
