package com.tcc.api_visa.scraper.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HostThrottleTest {

    private static final Duration DELAY = Duration.ofMillis(200);

    @Test
    void waitsBetweenRequestsToTheSameHost() throws InterruptedException {
        HostThrottle throttle = new HostThrottle(DELAY);

        long start = System.nanoTime();
        throttle.awaitTurn("www.gov.uk");
        throttle.awaitTurn("www.gov.uk");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(DELAY);
    }

    @Test
    void doesNotDelayDifferentHosts() throws InterruptedException {
        HostThrottle throttle = new HostThrottle(Duration.ofSeconds(10));

        long start = System.nanoTime();
        throttle.awaitTurn("www.gov.uk");
        throttle.awaitTurn("www.canada.ca");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }
}
