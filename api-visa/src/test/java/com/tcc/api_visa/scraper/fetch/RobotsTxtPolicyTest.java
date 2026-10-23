package com.tcc.api_visa.scraper.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcc.api_visa.scraper.ScraperProperties;
import crawlercommons.robots.BaseRobotRules;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RobotsTxtPolicyTest {

    private static final String ROBOTS_URL = "https://example.gov/robots.txt";

    private final RobotsTxtPolicy policy = new RobotsTxtPolicy(
            new ScraperProperties(
                    "api-visa-tcc/0.1 (+https://github.com/x/y)", Duration.ofSeconds(5), Duration.ZERO, 1024, true),
            Clock.systemUTC());

    @Test
    void usesTheFirstUserAgentTokenAsRobotName() {
        assertThat(policy.robotName()).isEqualTo("api-visa-tcc");
    }

    @Test
    void appliesRulesForAllAgents() {
        BaseRobotRules rules = parse(200, "User-agent: *\nDisallow: /search/\n");

        assertThat(rules.isAllowed("https://example.gov/visas/list.html")).isTrue();
        assertThat(rules.isAllowed("https://example.gov/search/all")).isFalse();
    }

    @Test
    void appliesRulesAddressedToOurRobot() {
        BaseRobotRules rules = parse(200, "User-agent: api-visa-tcc\nDisallow: /\n\nUser-agent: *\nAllow: /\n");

        assertThat(rules.isAllowed("https://example.gov/visas/list.html")).isFalse();
    }

    @Test
    void allowsEverythingWhenRobotsTxtDoesNotExist() {
        assertThat(parse(404, "").isAllowed("https://example.gov/anything")).isTrue();
    }

    @Test
    void disallowsEverythingWhenRobotsTxtIsUnavailable() {
        assertThat(parse(503, "").isAllowed("https://example.gov/anything")).isFalse();
    }

    private BaseRobotRules parse(int status, String body) {
        return policy.parse(ROBOTS_URL, status, body.getBytes(StandardCharsets.UTF_8), "text/plain");
    }
}
