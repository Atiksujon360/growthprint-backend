package com.growthprint.backend.ai;

import com.growthprint.backend.ai.provider.AiEndpointGuard;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Endpoint guards for tests with no real DNS: "localhost" and IP literals resolve to themselves, every other host name
 * resolves to a fixed public documentation-style address.
 */
public final class TestEndpointGuards {

    private static final byte[] PUBLIC = {93, (byte) 184, (byte) 216, 34};

    private TestEndpointGuards() {
    }

    /** Development-like: loopback and plain http to loopback are allowed (the fake provider server runs on loopback). */
    public static AiEndpointGuard allowLocal() {
        return new AiEndpointGuard(true, TestEndpointGuards::resolve);
    }

    /** Production-like: only public https endpoints. */
    public static AiEndpointGuard strict() {
        return new AiEndpointGuard(false, TestEndpointGuards::resolve);
    }

    public static InetAddress[] resolve(String host) {
        try {
            if (host.equals("localhost")) {
                return new InetAddress[]{InetAddress.getLoopbackAddress()};
            }
            if (host.matches("[0-9.]+") || host.contains(":")) {
                return new InetAddress[]{InetAddress.getByName(host)};
            }
            return new InetAddress[]{InetAddress.getByAddress(host, PUBLIC)};
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
