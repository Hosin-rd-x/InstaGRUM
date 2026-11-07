package com.instaos.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * An Instagram web session, harvested from the login WebView's cookies.
 *
 * Only cookie values are stored — never a password. The `sessionid` is
 * HttpOnly, so JavaScript in the page cannot read it; Android's
 * [android.webkit.CookieManager.getCookie] can, when called with the full
 * `https://www.instagram.com/` URL (web-method-report.md §3).
 */
data class WebSession(
    val sessionid: String,
    val csrftoken: String = "",
    val dsUserId: String = "",
    val mid: String = "",
    val igDid: String = "",
    /** Instagram username, filled in after login when the API confirms it. */
    val username: String = "",
) {
    val isLoggedIn: Boolean get() = sessionid.isNotBlank()

    /** `Cookie:` header value for web requests made with this session. */
    fun cookieHeader(): String = buildList {
        add("sessionid=$sessionid")
        if (csrftoken.isNotBlank()) add("csrftoken=$csrftoken")
        if (dsUserId.isNotBlank()) add("ds_user_id=$dsUserId")
        if (mid.isNotBlank()) add("mid=$mid")
        if (igDid.isNotBlank()) add("ig_did=$igDid")
    }.joinToString("; ")

    companion object {
        /**
         * Parse a `CookieManager.getCookie()` header (`name=value; ...`).
         * Returns null when there is no `sessionid` — i.e. not logged in.
         * The header carries no expiry dates, so validity is checked by
         * using the session, not by reading this string.
         */
        fun fromCookieHeader(header: String?): WebSession? {
            if (header.isNullOrBlank()) return null
            val values = header.split(";").mapNotNull { part ->
                val trimmed = part.trim()
                val equals = trimmed.indexOf('=')
                if (equals <= 0) null else trimmed.substring(0, equals).trim() to
                    trimmed.substring(equals + 1).trim()
            }.toMap()
            val sessionid = values["sessionid"].orEmpty()
            if (sessionid.isBlank()) return null
            return WebSession(
                sessionid = sessionid,
                csrftoken = values["csrftoken"].orEmpty(),
                dsUserId = values["ds_user_id"].orEmpty(),
                mid = values["mid"].orEmpty(),
                igDid = values["ig_did"].orEmpty(),
            )
        }
    }
}

/**
 * Encrypted storage for the web session (phase 2, audit §8 step 2).
 *
 * Uses EncryptedSharedPreferences (AES256-SIV keys, AES256-GCM values).
 * The file is intentionally NOT in the backup rules, so a session cookie
 * is never restored onto another device (Instagram would read that as a
 * hijack). [clear] signs out.
 *
 * Tests use [SessionStore] directly with a plain [SharedPreferences];
 * production code must go through [create].
 */
class SessionStore(private val prefs: SharedPreferences) {
    fun load(): WebSession? {
        val sessionid = prefs.getString(KEY_SESSIONID, "").orEmpty()
        if (sessionid.isBlank()) return null
        return WebSession(
            sessionid = sessionid,
            csrftoken = prefs.getString(KEY_CSRFTOKEN, "").orEmpty(),
            dsUserId = prefs.getString(KEY_DS_USER_ID, "").orEmpty(),
            mid = prefs.getString(KEY_MID, "").orEmpty(),
            igDid = prefs.getString(KEY_IG_DID, "").orEmpty(),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
        )
    }

    fun save(session: WebSession) {
        prefs.edit()
            .putString(KEY_SESSIONID, session.sessionid)
            .putString(KEY_CSRFTOKEN, session.csrftoken)
            .putString(KEY_DS_USER_ID, session.dsUserId)
            .putString(KEY_MID, session.mid)
            .putString(KEY_IG_DID, session.igDid)
            .putString(KEY_USERNAME, session.username)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "instaos_session"
        private const val KEY_SESSIONID = "sessionid"
        private const val KEY_CSRFTOKEN = "csrftoken"
        private const val KEY_DS_USER_ID = "ds_user_id"
        private const val KEY_MID = "mid"
        private const val KEY_IG_DID = "ig_did"
        private const val KEY_USERNAME = "username"

        /**
         * Production store. If encrypted storage is unavailable on the
         * device (very old KeyStore, etc.), falls back to a private
         * (MODE_PRIVATE, never backed up) preferences file rather than
         * crashing login — the session still never leaves the device.
         */
        fun create(context: Context): SessionStore {
            val encrypted = runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrNull()
            val prefs = encrypted ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return SessionStore(prefs)
        }
    }
}
