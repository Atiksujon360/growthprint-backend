package com.growthprint.backend.ai.provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.function.Function;

/**
 * Decides whether the admin-supplied endpoint of the custom OpenAI-compatible provider may be called by the server
 * (SSRF protection). Only https is accepted, and the host must resolve to public addresses: loopback, private,
 * link-local (including the cloud metadata address), carrier-grade NAT, unique-local IPv6 and unspecified addresses
 * are refused. Development may allow local addresses and plain http for localhost with
 * {@code app.ai.allow-local-endpoints=true}; metadata and link-local addresses stay refused even then.
 *
 * <p>The check runs when settings are saved and again right before every call. A host that changes its DNS answer
 * between the check and the connection (DNS rebinding) cannot be fully excluded without pinning the connection to the
 * checked address; redirects are disabled and the endpoint can only be set by an administrator.
 */
@Component
public class AiEndpointGuard {

    private final boolean allowLocal;
    private final Function<String, InetAddress[]> resolver;

    @Autowired
    public AiEndpointGuard(@Value("${app.ai.allow-local-endpoints:false}") boolean allowLocal) {
        this(allowLocal, AiEndpointGuard::resolveSystem);
    }

    /** For tests: supply a resolver so no real DNS lookup happens. */
    public AiEndpointGuard(boolean allowLocal, Function<String, InetAddress[]> resolver) {
        this.allowLocal = allowLocal;
        this.resolver = resolver;
    }

    /** @throws IllegalArgumentException with a message safe to show to the admin */
    public void requireAllowed(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("The base URL isn't a valid address.");
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.apply(host.startsWith("[") ? host.substring(1, host.length() - 1) : host);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("The host in the base URL couldn't be found.");
        }
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("The host in the base URL couldn't be found.");
        }
        boolean allLoopback = true;
        for (InetAddress address : addresses) {
            if (isAlwaysBlocked(address) || (!allowLocal && isNonPublic(address))) {
                throw new IllegalArgumentException(AiErrorCode.ENDPOINT_NOT_ALLOWED.message());
            }
            allLoopback &= address.isLoopbackAddress();
        }
        boolean https = scheme.equals("https");
        boolean localHttp = scheme.equals("http") && allowLocal && allLoopback;
        if (!https && !localHttp) {
            throw new IllegalArgumentException("The base URL must use https://.");
        }
    }

    /** Same check for a call-time failure: surfaces as an {@link AiException} instead of a validation message. */
    public void requireAllowedForCall(URI uri) {
        try {
            requireAllowed(uri);
        } catch (IllegalArgumentException e) {
            throw new AiException(AiErrorCode.ENDPOINT_NOT_ALLOWED);
        }
    }

    private static boolean isAlwaysBlocked(InetAddress a) {
        // Link-local covers 169.254.0.0/16 (cloud metadata 169.254.169.254) and fe80::/10; "any" is 0.0.0.0 / ::.
        if (a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        // An IPv4-mapped/compatible IPv6 address reaches its embedded IPv4 target, so the IPv4
        // always-blocked ranges apply to it as well (this holds even when local endpoints are allowed).
        if (a instanceof Inet6Address) {
            byte[] embedded = embeddedIpv4(a.getAddress());
            return embedded != null && isAlwaysBlockedIpv4(embedded);
        }
        return false;
    }

    private static boolean isAlwaysBlockedIpv4(byte[] b) {
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;
        return first == 0                              // 0.0.0.0/8 "this network"
                || (first == 169 && second == 254)    // 169.254.0.0/16 link-local (cloud metadata)
                || first >= 224;                      // 224.0.0.0/4 multicast and 240.0.0.0/4 reserved
    }

    private static boolean isNonPublic(InetAddress a) {
        if (a.isLoopbackAddress() || a.isSiteLocalAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            return isNonPublicIpv4(b);
        }
        if (a instanceof Inet6Address) {
            // IPv4-mapped (::ffff:a.b.c.d) and IPv4-compatible (::a.b.c.d) forms tunnel to IPv4:
            // judge them by the embedded address, not by the IPv6 rules.
            byte[] embedded = embeddedIpv4(b);
            if (embedded != null) {
                return isNonPublicIpv4(embedded);
            }
            return (b[0] & 0xfe) == 0xfc                 // fc00::/7 unique-local
                    || isTeredo(b)                       // 2001::/32, embeds IPv4 addresses
                    || is6to4(b);                        // 2002::/16, bytes 2..5 are an IPv4 address
        }
        return false;
    }

    private static boolean isNonPublicIpv4(byte[] b) {
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;
        int third = b[2] & 0xff;
        return first == 0                                            // 0.0.0.0/8
                || first == 10                                       // 10.0.0.0/8 private
                || first == 127                                      // 127.0.0.0/8 loopback
                || (first == 169 && second == 254)                   // 169.254.0.0/16 link-local
                || (first == 172 && second >= 16 && second <= 31)    // 172.16.0.0/12 private
                || (first == 192 && second == 168)                   // 192.168.0.0/16 private
                || (first == 100 && second >= 64 && second <= 127)   // 100.64.0.0/10 carrier-grade NAT
                || (first == 192 && second == 0 && third == 0)       // 192.0.0.0/24
                || (first == 198 && (second == 18 || second == 19))  // 198.18.0.0/15 benchmarking
                || first >= 224;                                     // multicast and reserved
    }

    /**
     * The embedded IPv4 address of an IPv4-mapped (::ffff:a.b.c.d) or IPv4-compatible (::a.b.c.d)
     * IPv6 address, or null for a native IPv6 address. Both forms are deprecated but still routed
     * by dual-stack stacks, so they must not smuggle a private IPv4 target past the IPv6 rules.
     */
    private static byte[] embeddedIpv4(byte[] b) {
        boolean prefixZero = true;
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                prefixZero = false;
                break;
            }
        }
        if (prefixZero && b[10] == (byte) 0xff && b[11] == (byte) 0xff) {
            return new byte[]{b[12], b[13], b[14], b[15]};
        }
        for (int i = 0; i < 12; i++) {
            if (b[i] != 0) {
                return null;
            }
        }
        return new byte[]{b[12], b[13], b[14], b[15]};
    }

    private static boolean isTeredo(byte[] b) {
        return (b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x01
                && (b[2] & 0xff) == 0x00 && (b[3] & 0xff) == 0x00;
    }

    private static boolean is6to4(byte[] b) {
        return (b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x02;
    }

    private static InetAddress[] resolveSystem(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("unresolvable");
        }
    }
}
