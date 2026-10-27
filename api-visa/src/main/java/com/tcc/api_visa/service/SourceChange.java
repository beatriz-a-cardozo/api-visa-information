package com.tcc.api_visa.service;

import org.jspecify.annotations.Nullable;

/**
 * An official source as sent by an administrator.
 *
 * @param verified {@code true} when the administrator has just confirmed the URL is official and
 *                 works; it sets {@code verifiedAt} to now. {@code false} clears it.
 */
public record SourceChange(
        String url, String description, @Nullable String language, boolean verified) {}
