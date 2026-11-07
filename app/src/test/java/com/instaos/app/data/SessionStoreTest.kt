package com.instaos.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2: the session store. Cookie parsing is pure and pinned here;
 * storage round-trips through plain preferences in tests (production
 * uses the encrypted store via [SessionStore.create]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun cookieHeaderParsesAllSessionCookies() {
        val session = WebSession.fromCookieHeader(
            "csrftoken=tok123; sessionid=abc123; ds_user_id=42; mid=m1; ig_did=d1",
        )
        assertNotNull(session)
        assertEquals("abc123", session!!.sessionid)
        assertEquals("tok123", session.csrftoken)
        assertEquals("42", session.dsUserId)
        assertEquals("m1", session.mid)
        assertEquals("d1", session.igDid)
        assertTrue(session.isLoggedIn)
        assertTrue(session.cookieHeader().contains("sessionid=abc123"))
        assertTrue(session.cookieHeader().contains("csrftoken=tok123"))
    }

    @Test
    fun noSessionIdMeansNotLoggedIn() {
        assertNull(WebSession.fromCookieHeader("csrftoken=tok123; mid=m1"))
        assertNull(WebSession.fromCookieHeader(null))
        assertNull(WebSession.fromCookieHeader(""))
        assertNull(WebSession.fromCookieHeader("  "))
    }

    @Test
    fun sessionRoundTripsAndClears() {
        val prefs = context.getSharedPreferences("test-session", Context.MODE_PRIVATE).apply { edit().clear().apply() }
        val store = SessionStore(prefs)
        assertNull(store.load())

        val session = WebSession("sess-1", "csrf-1", "77", "mid-1", "did-1", "someone")
        store.save(session)
        val loaded = store.load()
        assertEquals(session, loaded)
        assertEquals("someone", loaded?.username)

        store.clear()
        assertNull(store.load())
    }
}
