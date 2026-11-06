package com.tcc.api_visa.scraper.support;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a country name as written on a government page ("Brazil", "Südafrika", "Korea, Republic
 * of") into its ISO alpha-3 code. Names come from the JDK's country names in the page's language,
 * plus the official forms the JDK does not use.
 */
public final class CountryNames {

    /**
     * Official, older or long names used by government lists, normalized. Keys may include the text in
     * parentheses, for lists that write "Korea (North)" or "Kongo (Republik Kongo)".
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            // English
            Map.entry("republic of korea", "KOR"),
            Map.entry("korea republic of", "KOR"),
            Map.entry("korea south", "KOR"),
            Map.entry("korea north", "PRK"),
            Map.entry("peoples republic of china", "CHN"),
            Map.entry("china peoples republic of", "CHN"),
            Map.entry("hong kong special administrative region", "HKG"),
            Map.entry("macao special administrative region", "MAC"),
            Map.entry("united states of america", "USA"),
            Map.entry("bosnia herzegovina", "BIH"),
            Map.entry("cape verde", "CPV"),
            Map.entry("cabo verde", "CPV"),
            Map.entry("turkey", "TUR"),
            Map.entry("turkiye", "TUR"),
            Map.entry("russia", "RUS"),
            Map.entry("vietnam", "VNM"),
            Map.entry("ivory coast", "CIV"),
            Map.entry("czech republic", "CZE"),
            Map.entry("czech", "CZE"),
            Map.entry("macedonia", "MKD"),
            Map.entry("russian federation", "RUS"),
            Map.entry("vatican", "VAT"),
            Map.entry("united kingdom of great britain northern ireland", "GBR"),
            Map.entry("hong kong", "HKG"),
            Map.entry("kingdom of eswatini", "SWZ"),
            Map.entry("sierre leone", "SLE"), // misspelled on the South African list
            Map.entry("british virgin island", "VGB"),
            Map.entry("virgin islands british", "VGB"),
            Map.entry("south georgia the south sandwich islands", "SGS"),
            Map.entry("brunei darussalam", "BRN"),
            Map.entry("maldives islands", "MDV"),
            Map.entry("palestine", "PSE"),
            Map.entry("vatican city state", "VAT"),
            Map.entry("british citizen", "GBR"),
            Map.entry("sao tome e principe", "STP"),
            Map.entry("st vincent the grenadines", "VCT"),
            Map.entry("congo", "COG"),
            Map.entry("congo republic of", "COG"),
            Map.entry("republic of the congo", "COG"),
            Map.entry("congo democratic republic of", "COD"),
            Map.entry("democratic republic of the congo", "COD"),
            // German
            Map.entry("korea republik korea sudkorea", "KOR"),
            Map.entry("korea demokratische volksrepublik nordkorea", "PRK"),
            Map.entry("kongo republik kongo", "COG"),
            Map.entry("kongo demokratische republik", "COD"),
            Map.entry("hongkong", "HKG"),
            Map.entry("macau", "MAC"),
            Map.entry("moldau", "MDA"),
            Map.entry("palastinensische gebiete", "PSE"),
            Map.entry("russische foderation", "RUS"),
            Map.entry("slowakische republik", "SVK"),
            Map.entry("tschechische republik", "CZE"),
            Map.entry("st vincent u grenadinen", "VCT"),
            Map.entry("vereinigtes konigreich grossbritannien nordirland", "GBR"),
            Map.entry("vereinigte staaten von amerika", "USA"));

    private static final Map<Locale, Map<String, String>> BY_LANGUAGE = new ConcurrentHashMap<>();

    private CountryNames() {}

    /** ISO alpha-3 code of a country name in {@code language}, or empty when the name is not recognized. */
    public static Optional<String> iso3(String name, Locale language) {
        String key = normalize(name);
        String alias = ALIASES.getOrDefault(
                key, ALIASES.get(normalize(name.replace("(", " ").replace(")", " "))));
        if (alias != null) {
            return Optional.of(alias);
        }
        Map<String, String> names = names(language);
        String code = names.get(key);
        if (code == null && name.contains(",")) {
            // "Cameroon, Republic of", "Saudi Arabia, Kingdom of": the part before the comma is the name.
            code = names.get(normalize(name.substring(0, name.indexOf(','))));
        }
        return Optional.ofNullable(code);
    }

    /**
     * Lowercase, no accents or punctuation, without notes in parentheses or footnote marks: "Côte
     * d’Ivoire (formerly Ivory Coast)" becomes "cote divoire", "Taiwan*" becomes "taiwan". "And"
     * and "&" are dropped and "Saint" becomes "st", so "Trinidad & Tobago" matches "Trinidad and
     * Tobago" and "St. Lucia" matches "Saint Lucia".
     */
    static String normalize(String name) {
        String withoutNotes = name.replaceAll("\\(.*", "").replace("*", "");
        String withoutAccents = Normalizer.normalize(withoutNotes, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("ß", "ss");
        return (" " + withoutAccents.toLowerCase(Locale.ROOT).replaceAll("['’`]", "") + " ")
                .replaceAll("[^a-z0-9]+", " ")
                .replace(" and ", " ")
                .replace(" saint ", " st ")
                .replace(" und ", " ")
                .strip();
    }

    private static Map<String, String> names(Locale language) {
        return BY_LANGUAGE.computeIfAbsent(language, locale -> {
            Map<String, String> names = new HashMap<>();
            for (String iso2 : Locale.getISOCountries()) {
                Locale country = Locale.of("", iso2);
                names.put(normalize(country.getDisplayCountry(locale)), country.getISO3Country());
            }
            return Map.copyOf(names);
        });
    }
}
