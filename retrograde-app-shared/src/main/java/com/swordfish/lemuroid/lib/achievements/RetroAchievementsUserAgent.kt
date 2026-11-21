package com.swordfish.lemuroid.lib.achievements

import android.os.Build

object RetroAchievementsUserAgent {
    const val PRODUCT_NAME = "Arcadune"
    const val DEFAULT_APP_VERSION = "1.0.9"
    const val DEFAULT_RCHEEVOS_CLAUSE = "rcheevos/12.5"

    fun build(
        appVersion: String = DEFAULT_APP_VERSION,
        osRelease: String? = Build.VERSION.RELEASE,
        coreName: String? = null,
        coreVersion: String? = null,
        rcheevosClause: String? = null,
    ): String {
        val safeAppVer = sanitizeToken(appVersion, replaceSpaces = true).ifEmpty { DEFAULT_APP_VERSION }
        val safeOs = sanitizeToken(osRelease.orEmpty(), replaceSpaces = false)
        val platformClause = if (safeOs.isNotEmpty()) "Android $safeOs" else "Android"

        val sb = StringBuilder()
        sb.append(PRODUCT_NAME).append('/').append(safeAppVer)
        sb.append(" (").append(platformClause).append(')')

        val safeCoreName = sanitizeToken(coreName.orEmpty(), replaceSpaces = true)
        val safeCoreVer = sanitizeToken(coreVersion.orEmpty(), replaceSpaces = true)
        if (safeCoreName.isNotEmpty()) {
            sb.append(' ').append(safeCoreName)
            if (safeCoreVer.isNotEmpty()) {
                sb.append('/').append(safeCoreVer)
            }
        }

        val safeRcClause = sanitizeToken(rcheevosClause.orEmpty(), replaceSpaces = false)
        if (safeRcClause.isNotEmpty()) {
            sb.append(' ').append(safeRcClause)
        }

        return sb.toString()
    }

    fun redactSecrets(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        return raw
            .replace(Regex("(?<=^|[?&\\s])t=[^&\\s\"]+"), "t=[REDACTED]")
            .replace(Regex("(?<=^|[?&\\s])p=[^&\\s\"]+"), "p=[REDACTED]")
            .replace(Regex("\"Token\"\\s*:\\s*\"[^\"]+\""), "\"Token\":\"[REDACTED]\"")
    }

    private fun sanitizeToken(
        input: String,
        replaceSpaces: Boolean,
    ): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input.trim()) {
            if (ch.code < 0x20 || ch.code == 0x7F) continue
            when {
                ch == ' ' && replaceSpaces -> sb.append('_')
                ch == '/' && replaceSpaces -> sb.append('-')
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim('_', ' ')
    }
}
