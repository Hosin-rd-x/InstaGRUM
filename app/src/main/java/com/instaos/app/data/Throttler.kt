package com.instaos.app.data

/**
 * Pacing for Instagram web requests (phase 2, audit §8 step 2).
 *
 * Proven in the web-method test: even an authenticated session gets 429
 * and then `400 spam:true` if it paginates too fast. So: a minimum gap
 * between feed calls, and exponential back-off after a 429 — the next
 * call returns [RateLimited] immediately instead of hammering the server.
 *
 * Pure Kotlin with an injectable clock, so unit tests can drive time.
 */
class Throttler(
    private val minIntervalMs: Long = 2_500,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private var lastCallMs: Long = 0
    private var blockedUntilMs: Long = 0
    private var consecutiveRateLimits: Int = 0

    /** Milliseconds to wait before the next call (0 = go ahead). */
    fun waitMs(): Long {
        val now = nowMs()
        if (now < blockedUntilMs) return blockedUntilMs - now
        if (lastCallMs == 0L) return 0
        return (minIntervalMs - (now - lastCallMs)).coerceAtLeast(0)
    }

    fun recordCall() {
        lastCallMs = nowMs()
    }

    /** A 429 arrived. [retryAfterSeconds] comes from the Retry-After header when present. */
    fun recordRateLimited(retryAfterSeconds: Long?) {
        consecutiveRateLimits++
        val backoffSeconds = retryAfterSeconds?.coerceIn(1, 300)
            ?: (15L * (1L shl (consecutiveRateLimits - 1).coerceAtMost(4))).coerceAtMost(300)
        blockedUntilMs = nowMs() + backoffSeconds * 1_000
    }

    fun recordSuccess() {
        consecutiveRateLimits = 0
    }
}
