package com.tcc.api_visa.scraper.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Helpers to read the "heading followed by paragraphs and lists" layout used by most government pages. */
public final class HtmlSections {

    private HtmlSections() {}

    /**
     * Elements after the first {@code headingTag} whose text matches, up to the next heading of the
     * same tag. Empty if no heading matches.
     */
    public static Optional<List<Element>> section(Document document, String headingTag, Predicate<String> headingText) {
        return document.select(headingTag).stream()
                .filter(heading -> headingText.test(clean(heading.text())))
                .findFirst()
                .map(heading -> {
                    List<Element> elements = new ArrayList<>();
                    for (Element current = heading.nextElementSibling();
                            current != null && !current.tagName().equals(headingTag);
                            current = current.nextElementSibling()) {
                        elements.add(current);
                    }
                    return elements;
                });
    }

    /**
     * Items of the lists introduced by a paragraph containing {@code marker} (for example "must"),
     * which is how pages separate mandatory requirements from optional information.
     */
    public static List<String> listItemsIntroducedBy(List<Element> section, String marker) {
        String lowerMarker = marker.toLowerCase(Locale.ROOT);
        List<String> items = new ArrayList<>();
        Element previous = null;
        for (Element element : section) {
            boolean introduced = previous != null
                    && previous.tagName().equals("p")
                    && previous.text().toLowerCase(Locale.ROOT).contains(lowerMarker);
            if (element.tagName().equals("ul") && introduced) {
                element.select("> li").stream()
                        .map(item -> clean(item.text()))
                        .filter(text -> !text.isEmpty())
                        .forEach(items::add);
            }
            previous = element;
        }
        return items;
    }

    /** Collapses whitespace and removes the space that inline links leave before punctuation. */
    public static String clean(String text) {
        return text.replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll(" ([,.;:])", "$1")
                .trim();
    }
}
