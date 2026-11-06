package com.tcc.api_visa.scraper.fetch;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Keeps a minimum interval between requests to the same host, so we never overload a site. */
class HostThrottle {

    private final long minDelayNanos;
    private final Map<String, Slot> slots = new ConcurrentHashMap<>();

    HostThrottle(Duration minDelay) {
        this.minDelayNanos = minDelay.toNanos();
    }

    void awaitTurn(String host) throws InterruptedException {
        Slot slot = slots.computeIfAbsent(host, ignored -> new Slot());
        slot.lock.lock();
        try {
            long waitNanos = slot.lastRequestNanos + minDelayNanos - System.nanoTime();
            if (slot.used && waitNanos > 0) {
                Thread.sleep(Duration.ofNanos(waitNanos));
            }
            slot.lastRequestNanos = System.nanoTime();
            slot.used = true;
        } finally {
            slot.lock.unlock();
        }
    }

    private static final class Slot {
        private final ReentrantLock lock = new ReentrantLock();
        private long lastRequestNanos;
        private boolean used;
    }
}
