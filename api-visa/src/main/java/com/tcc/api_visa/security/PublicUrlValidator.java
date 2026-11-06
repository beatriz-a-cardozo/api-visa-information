package com.tcc.api_visa.security;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Accepts only URLs that are safe to store and fetch: absolute {@code https} on the default port,
 * a host name (not an IP) and every address of that host on the public internet. Without this, a
 * registered URL could make the server read its own network (SSRF), such as the cloud metadata
 * endpoint or the database.
 *
 * <p>DNS can change between this check and the request. That is why the fetcher checks again before
 * every request, including each redirect.
 */
@Component
public class PublicUrlValidator {

    static final int MAX_LENGTH = 2048;

    /** Labels of letters, digits and hyphens, ending in an alphabetic top-level domain. */
    private static final Pattern HOST_NAME = Pattern.compile("([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}\\.?");

    private final HostResolver resolver;

    public PublicUrlValidator(HostResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * @return the parsed URL
     * @throws UnsafeUrlException if the URL is not allowed
     */
    public URI validate(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_LENGTH) {
            throw new UnsafeUrlException(String.valueOf(url), "empty or longer than " + MAX_LENGTH);
        }
        URI uri = parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new UnsafeUrlException(url, "only https is accepted");
        }
        if (uri.getRawUserInfo() != null) {
            throw new UnsafeUrlException(url, "credentials in the URL");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new UnsafeUrlException(url, "only the default https port");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!HOST_NAME.matcher(host).matches()) {
            throw new UnsafeUrlException(url, "the host must be a public domain name, not an IP address");
        }

        List<InetAddress> addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new UnsafeUrlException(url, "the host does not exist");
        }
        // Every address, not just the first: the HTTP client may connect to any of them.
        if (addresses.isEmpty() || !addresses.stream().allMatch(PublicUrlValidator::isPublic)) {
            throw new UnsafeUrlException(url, "the host points to a private or reserved network");
        }
        return uri;
    }

    private static URI parse(String url) {
        try {
            URI uri = new URI(url.strip());
            if (!uri.isAbsolute() || uri.isOpaque()) {
                throw new UnsafeUrlException(url, "not an absolute URL");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new UnsafeUrlException(url, "not a valid URL");
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first != 0 // "this network"
                    && first < 240 // reserved and broadcast
                    && !(first == 100 && second >= 64 && second <= 127) // carrier-grade NAT 100.64.0.0/10
                    && !(first == 192 && second == 0 && (bytes[2] & 0xff) == 0) // IETF 192.0.0.0/24
                    && !(first == 198 && (second == 18 || second == 19)); // benchmarking 198.18.0.0/15
        }
        if ((bytes[0] & 0xfe) == 0xfc) {
            return false; // unique local fc00::/7 (Java's "site local" is only the old fec0::/10)
        }
        if (isNat64(bytes)) {
            // 64:ff9b::/96 carries an IPv4 address in its last 4 bytes.
            return isPublic(ipv4(Arrays.copyOfRange(bytes, 12, 16)));
        }
        return true;
    }

    private static boolean isNat64(byte[] bytes) {
        byte[] prefix = {0, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0};
        return Arrays.equals(bytes, 0, 12, prefix, 0, 12);
    }

    private static InetAddress ipv4(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("4 bytes are always a valid IPv4 address", e);
        }
    }
}
