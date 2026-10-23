package com.tcc.api_visa.domain;

import java.time.Instant;

/**
 * An official government page that publishes visa information for a destination.
 *
 * @param verifiedAt last time someone confirmed the URL works and is official; {@code null} if never
 */
public record OfficialSource(String url, String description, String language, Instant verifiedAt) {}
