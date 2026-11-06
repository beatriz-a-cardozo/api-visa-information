package com.tcc.api_visa.support;

import com.tcc.api_visa.scraper.ScrapingException;
import com.tcc.api_visa.scraper.fetch.HtmlFetcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Serves saved copies of official pages from {@code src/test/resources/fixtures}, so tests never hit the network. */
public class FixtureHtmlFetcher implements HtmlFetcher {

    private final Map<String, String> htmlByUrl = new HashMap<>();

    public FixtureHtmlFetcher withFixture(String url, String fixturePath) {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/" + fixturePath)) {
            if (input == null) {
                throw new IllegalArgumentException("Fixture not found: " + fixturePath);
            }
            htmlByUrl.put(url, new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public FixtureHtmlFetcher withHtml(String url, String html) {
        htmlByUrl.put(url, html);
        return this;
    }

    @Override
    public Document fetch(String url) {
        String html = htmlByUrl.get(url);
        if (html == null) {
            throw new ScrapingException("No fixture for " + url);
        }
        return Jsoup.parse(html, url);
    }
}
