package com.tcc.api_visa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class PublicUrlValidatorTest {

    /** Fake DNS: a few names point to private addresses, every other name to a public one. */
    private static final Map<String, String> DNS = Map.of(
            "metadata.example.org", "169.254.169.254",
            "intranet.example.org", "192.168.0.10",
            "mixed.example.org", "8.8.8.8",
            "ula.example.org", "fd12:3456::1",
            "nat64.example.org", "64:ff9b::a00:1",
            "cgnat.example.org", "100.100.0.1");

    private final PublicUrlValidator validator = new PublicUrlValidator(PublicUrlValidatorTest::resolve);

    private static List<InetAddress> resolve(String host) throws UnknownHostException {
        if (host.equals("missing.example.org")) {
            throw new UnknownHostException(host);
        }
        if (host.equals("mixed.example.org")) {
            // One public and one private address: the client could connect to either.
            return List.of(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1"));
        }
        return List.of(InetAddress.getByName(DNS.getOrDefault(host, "93.184.215.14")));
    }

    @Test
    void acceptsPublicHttpsUrls() {
        assertThat(validator.validate("https://www.gov.uk/standard-visitor").getHost())
                .isEqualTo("www.gov.uk");
        assertThat(validator.validate("https://www.gov.uk:443/check-uk-visa?country=bra#result"))
                .isNotNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "http://www.gov.uk/",
                "file:///etc/passwd",
                "gopher://www.gov.uk/",
                "/relative/path",
                "https://exa mple.gov",
                "https://user:secret@www.gov.uk/",
                "https://www.gov.uk:8443/",
                "https://localhost/",
                "https://127.0.0.1/",
                "https://2130706433/",
                "https://0x7f.0.0.1/",
                "https://[::1]/",
                "https://metadata.example.org/latest/meta-data/",
                "https://intranet.example.org/",
                "https://mixed.example.org/",
                "https://ula.example.org/",
                "https://nat64.example.org/",
                "https://cgnat.example.org/",
                "https://missing.example.org/"
            })
    void rejectsUnsafeUrls(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void rejectsVeryLongUrls() {
        String url = "https://www.gov.uk/" + "a".repeat(PublicUrlValidator.MAX_LENGTH);

        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(UnsafeUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"0.0.0.0", "10.1.2.3", "172.16.0.1", "198.18.0.1", "192.0.0.8", "224.0.0.1", "255.255.255.255"})
    void reservedIpv4RangesAreNotPublic(String address) throws UnknownHostException {
        assertThat(PublicUrlValidator.isPublic(InetAddress.getByName(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "100.128.0.1", "2001:4860:4860::8888", "64:ff9b::808:808"})
    void publicAddressesAreAccepted(String address) throws UnknownHostException {
        assertThat(PublicUrlValidator.isPublic(InetAddress.getByName(address))).isTrue();
    }

    @Test
    void everyUrlInTheSeedCatalogIsAccepted() throws Exception {
        List<Map<String, Object>> countries;
        try (var input = getClass().getResourceAsStream("/seed/countries.json")) {
            countries = JsonMapper.builder().build().readValue(input, new TypeReference<>() {});
        }

        List<String> urls = countries.stream()
                .flatMap(country -> ((List<?>) country.get("officialSources")).stream())
                .map(source -> (String) ((Map<?, ?>) source).get("url"))
                .toList();

        assertThat(urls)
                .isNotEmpty()
                .allSatisfy(url -> assertThat(validator.validate(url)).isNotNull());
    }
}
