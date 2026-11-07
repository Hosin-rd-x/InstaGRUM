package com.instaos.app.data

import org.junit.Assert.*
import org.junit.Test

/** Phase 2: request pacing — the 429/back-off behaviour, on a fake clock. */
class ThrottlerTest {
    private var now = 1_000_000L
    private fun throttler() = Throttler(minIntervalMs = 1_000, nowMs = { now })

    @Test
    fun firstCallIsFreeThenMinIntervalApplies() {
        val t = throttler()
        assertEquals(0, t.waitMs())
        t.recordCall()
        assertEquals(1_000, t.waitMs())
        now += 1_000
        assertEquals(0, t.waitMs())
    }

    @Test
    fun rateLimitBlocksWithExponentialBackoff() {
        val t = throttler()
        t.recordCall()
        t.recordRateLimited(null)
        assertEquals(15_000, t.waitMs())
        now += 15_000
        assertEquals(0, t.waitMs())
        t.recordCall()
        t.recordRateLimited(null)
        assertEquals(30_000, t.waitMs())
    }

    @Test
    fun retryAfterHeaderWinsOverBackoff() {
        val t = throttler()
        t.recordRateLimited(5)
        assertEquals(5_000, t.waitMs())
    }

    @Test
    fun successResetsTheBackoffLadder() {
        val t = throttler()
        t.recordRateLimited(null)
        t.recordSuccess()
        t.recordRateLimited(null)
        assertEquals(15_000, t.waitMs())
    }
}
