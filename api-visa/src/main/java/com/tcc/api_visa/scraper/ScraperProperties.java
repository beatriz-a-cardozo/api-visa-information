package com.tcc.api_visa.scraper;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How the scrapers behave towards the official websites.
 *
 * @param userAgent         identifies the robot and links to the repository, so site owners know who we are
 * @param timeout           maximum time to wait for a page
 * @param minDelayPerHost   minimum interval between two requests to the same host
 * @param maxBodyBytes      pages larger than this are truncated
 * @param respectRobotsTxt  when {@code true}, pages disallowed by the site's robots.txt are not fetched
 */
@Validated
@ConfigurationProperties("api-visa.scraper")
public record ScraperProperties(
        @NotBlank String userAgent,
        @NotNull Duration timeout,
        @NotNull Duration minDelayPerHost,
        @Positive int maxBodyBytes,
        boolean respectRobotsTxt) {}
