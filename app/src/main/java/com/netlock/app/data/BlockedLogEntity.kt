package com.netlock.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per domain that got blocked while protection was running. Purely
 * diagnostic - lets the user see, right after something breaks, exactly
 * which domain was refused so they can decide whether to whitelist it
 * (e.g. a video CDN a study site depends on that isn't the site's own
 * domain, so it wasn't covered by whitelisting the site itself).
 */
@Entity(tableName = "blocked_log")
data class BlockedLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val timestampMillis: Long
)
