package com.tcc.api_visa.scraper.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

class HtmlSectionsTest {

    private static final Document PAGE = Jsoup.parse("""
            <h2>Intro</h2><p>ignored</p>
            <h2>Requirements</h2>
            <p>You must:</p>
            <ul><li>have a <a href="#">passport</a> , valid</li><li> </li></ul>
            <p>You may also:</p>
            <ul><li>optional item</li></ul>
            <h3>Sub heading stays in the section</h3>
            <h2>Next</h2><ul><li>other section</li></ul>
            """);

    @Test
    void returnsElementsUntilTheNextHeadingOfTheSameLevel() {
        List<Element> section =
                HtmlSections.section(PAGE, "h2", "Requirements"::equals).orElseThrow();

        assertThat(section).extracting(Element::tagName).containsExactly("p", "ul", "p", "ul", "h3");
    }

    @Test
    void isEmptyWhenNoHeadingMatches() {
        assertThat(HtmlSections.section(PAGE, "h2", "Missing"::equals)).isEmpty();
    }

    @Test
    void keepsOnlyListsIntroducedByTheMarker() {
        List<Element> section =
                HtmlSections.section(PAGE, "h2", "Requirements"::equals).orElseThrow();

        assertThat(HtmlSections.listItemsIntroducedBy(section, "MUST")).containsExactly("have a passport, valid");
    }

    @Test
    void cleansWhitespaceAndSpacesBeforePunctuation() {
        assertThat(HtmlSections.clean("  a  b ,\n c . ")).isEqualTo("a b, c.");
    }
}
