package com.jbassdev.bayboard.logic

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Timestamps are stored as ISO 8601 strings, matching JavaScript's
 * `Date#toISOString()` exactly (millisecond precision, trailing Z) so the
 * web app's string sorting keeps working on documents written from Android.
 */
object Iso {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun format(instant: Instant): String = formatter.format(instant)

    fun parse(value: String?): Instant? =
        value?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
