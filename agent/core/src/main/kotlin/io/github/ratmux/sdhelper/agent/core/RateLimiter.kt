package io.github.ratmux.sdhelper.agent.core

import java.time.Clock

/** Token bucket: [capacity] messages in a burst, refilled at [refillPerSecond]. One instance per connection. */
class RateLimiter(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val refillPerSecond: Double = DEFAULT_REFILL_PER_SECOND,
    private val clock: Clock = Clock.systemUTC(),
) {
    private var tokens = capacity.toDouble()
    private var lastRefillMillis = clock.millis()

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clock.millis()
        val elapsedSeconds = (now - lastRefillMillis) / MILLIS_PER_SECOND
        tokens = minOf(capacity.toDouble(), tokens + elapsedSeconds * refillPerSecond)
        lastRefillMillis = now
        if (tokens < 1.0) return false
        tokens -= 1.0
        return true
    }

    companion object {
        const val DEFAULT_CAPACITY = 60
        const val DEFAULT_REFILL_PER_SECOND = 30.0
        private const val MILLIS_PER_SECOND = 1000.0
    }
}
