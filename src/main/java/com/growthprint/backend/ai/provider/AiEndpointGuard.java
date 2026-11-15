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
        return a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isMulticastAddress();
    }

    private static boolean isNonPublic(InetAddress a) {
        if (a.isLoopbackAddress() || a.isSiteLocalAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0                                   // 0.0.0.0/8
                    || (first == 100 && second >= 64 && second <= 127)  // 100.64.0.0/10 carrier-grade NAT
                    || (first == 192 && second == 0 && (b[2] & 0xff) == 0) // 192.0.0.0/24
                    || (first == 198 && (second == 18 || second == 19))    // 198.18.0.0/15 benchmarking
                    || first >= 240;                                // reserved and broadcast
        }
        if (a instanceof Inet6Address) {
            return (b[0] & 0xfe) == 0xfc;                       // fc00::/7 unique-local
        }
        return false;
    }

    private static InetAddress[] resolveSystem(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("unresolvable");
        }
    }
}
