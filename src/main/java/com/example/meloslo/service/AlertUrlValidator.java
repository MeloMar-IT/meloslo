package com.example.meloslo.service;

import com.example.meloslo.util.MaskedUrlSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Refuses alert URLs that could be used to reach internal services (SSRF).
 * Messages are fixed texts and never contain the URL.
 */
@Component
public class AlertUrlValidator {

    /** Resolves a host name; replaced in tests so they need no DNS. */
    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final List<String> allowedHosts;
    private final HostResolver resolver;

    @Autowired
    public AlertUrlValidator(@Value("${app.alerting.allowed-hosts:}") String allowedHostsCsv) {
        this(allowedHostsCsv, InetAddress::getAllByName);
    }

    AlertUrlValidator(String allowedHostsCsv, HostResolver resolver) {
        this.allowedHosts = allowedHostsCsv == null ? List.of()
                : Arrays.stream(allowedHostsCsv.split(","))
                        .map(h -> normalizeHost(h))
                        .filter(h -> !h.isEmpty())
                        .toList();
        this.resolver = resolver;
    }

    /** A null or blank URL means "no alert URL" and is accepted. */
    public void validate(String url) {
        if (url == null || url.isBlank()) return;
        if (MaskedUrlSerializer.isMasked(url.trim())) {
            throw new BadRequestException("Enter the full alert URL");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (Exception e) {
            throw new BadRequestException("Alert URL is not valid");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BadRequestException("Alert URL must use http or https");
        }
        String host = uri.getHost() == null ? "" : normalizeHost(uri.getHost());
        if (host.isEmpty()) {
            throw new BadRequestException("Alert URL must contain a host");
        }
        if (!allowedHosts.isEmpty() && allowedHosts.stream().noneMatch(a -> host.equals(a) || host.endsWith("." + a))) {
            throw new BadRequestException("Alert URL host is not allowed");
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new BadRequestException("Alert URL host cannot be resolved");
        }
        if (addresses == null || addresses.length == 0) {
            throw new BadRequestException("Alert URL host cannot be resolved");
        }
        for (InetAddress a : addresses) {
            if (isForbiddenAddress(a)) {
                throw new BadRequestException("Alert URL points to a private or internal address");
            }
        }
    }

    /** Host for log lines; never the path or query. */
    public static String hostOf(String url) {
        try {
            String h = new URI(url.trim()).getHost();
            return h == null ? "<unparsable>" : h;
        } catch (Exception e) {
            return "<unparsable>";
        }
    }

    private static String normalizeHost(String host) {
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h;
    }

    static boolean isForbiddenAddress(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            return forbiddenV4(b);
        }
        if (a instanceof Inet6Address) {
            return forbiddenV6(b);
        }
        return true;
    }

    private static boolean forbiddenV4(byte[] b) {
        int o0 = b[0] & 0xff, o1 = b[1] & 0xff, o2 = b[2] & 0xff;
        return o0 == 0
                || (o0 == 100 && (o1 & 0xc0) == 64)          // 100.64.0.0/10
                || (o0 == 192 && o1 == 0 && o2 == 0)         // 192.0.0.0/24
                || (o0 == 192 && o1 == 0 && o2 == 2)         // 192.0.2.0/24
                || (o0 == 198 && o1 == 51 && o2 == 100)      // 198.51.100.0/24
                || (o0 == 203 && o1 == 0 && o2 == 113)       // 203.0.113.0/24
                || (o0 == 198 && (o1 & 0xfe) == 18)          // 198.18.0.0/15
                || (o0 == 192 && o1 == 88 && o2 == 99)       // 192.88.99.0/24
                || o0 >= 240;                                // 240.0.0.0/4, broadcast
    }

    private static boolean forbiddenV6(byte[] b) {
        int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
        if ((b0 & 0xfe) == 0xfc) return true;                              // fc00::/7
        if (b0 == 0x20 && b1 == 0x01 && b[2] == 0 && b[3] == 0) return true; // 2001::/32 Teredo
        if (b0 == 0x20 && b1 == 0x01 && b[2] == 0x0d && (b[3] & 0xff) == 0xb8) return true; // 2001:db8::/32
        if (b0 == 0x01 && b1 == 0 && allZero(b, 2, 8)) return true;        // 100::/64
        if (b0 == 0xfe && (b1 & 0xc0) == 0x80) return true;                // fe80::/10
        if (b0 == 0xff) return true;                                       // ff00::/8
        if (allZero(b, 0, 12)) return embeddedForbidden(b, 12);            // ::/96 (IPv4-compatible)
        if (allZero(b, 0, 10) && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
            return embeddedForbidden(b, 12);                               // ::ffff:0:0/96 (IPv4-mapped)
        }
        if (b0 == 0x00 && b1 == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b && allZero(b, 4, 12)) {
            return embeddedForbidden(b, 12);                               // 64:ff9b::/96 (NAT64)
        }
        if (b0 == 0x20 && b1 == 0x02) {
            return embeddedForbidden(b, 2);                                // 2002::/16 (6to4)
        }
        return false;
    }

    /** Applies the full IPv4 policy (including loopback and private ranges) to an embedded address. */
    private static boolean embeddedForbidden(byte[] b, int from) {
        try {
            return isForbiddenAddress(InetAddress.getByAddress(Arrays.copyOfRange(b, from, from + 4)));
        } catch (UnknownHostException e) {
            return true;
        }
    }

    private static boolean allZero(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) if (b[i] != 0) return false;
        return true;
    }
}
