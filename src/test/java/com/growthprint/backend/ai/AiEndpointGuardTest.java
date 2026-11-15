package com.growthprint.backend.ai;

import com.growthprint.backend.ai.provider.AiEndpointGuard;
import com.growthprint.backend.ai.provider.AiErrorCode;
import com.growthprint.backend.ai.provider.AiException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SSRF protection for the custom OpenAI-compatible endpoint: no real DNS, every address is stubbed. */
class AiEndpointGuardTest {

    private static AiEndpointGuard resolvingTo(boolean allowLocal, String... ips) {
        Function<String, InetAddress[]> resolver = host -> {
            try {
                InetAddress[] out = new InetAddress[ips.length];
                for (int i = 0; i < ips.length; i++) {
                    out[i] = InetAddress.getByName(ips[i]);
                }
                return out;
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        };
        return new AiEndpointGuard(allowLocal, resolver);
    }

    @Test
    void aPublicHttpsEndpointIsAllowed() {
        assertThatCode(() -> resolvingTo(false, "93.184.216.34").requireAllowed(URI.create("https://llm.example.com/v1")))
                .doesNotThrowAnyException();
    }

    @Test
    void privateLoopbackLinkLocalAndMetadataAddressesAreRefused() {
        String[] blocked = {
                "127.0.0.1", "10.1.2.3", "172.16.5.5", "192.168.1.10", "169.254.169.254", "100.64.0.1",
                "0.0.0.0", "198.18.0.5", "::1", "fe80::1", "fd12:3456:789a::1", "224.0.0.1"};
        for (String ip : blocked) {
            assertThatThrownBy(() -> resolvingTo(false, ip).requireAllowed(URI.create("https://x.example.com/v1")))
                    .as("address %s must be refused", ip)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(AiErrorCode.ENDPOINT_NOT_ALLOWED.message());
        }
    }

    @Test
    void oneBadAddressAmongPublicOnesIsEnoughToRefuse() {
        // A host that also answers with an internal address (split-horizon or rebinding setups).
        assertThatThrownBy(() -> resolvingTo(false, "93.184.216.34", "10.0.0.5").requireAllowed(URI.create("https://x.example.com/v1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void plainHttpToAPublicHostIsRefused() {
        assertThatThrownBy(() -> resolvingTo(false, "93.184.216.34").requireAllowed(URI.create("http://llm.example.com/v1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void developmentMayUseLoopbackIncludingPlainHttpButNeverTheMetadataAddress() {
        AiEndpointGuard dev = resolvingTo(true, "127.0.0.1");
        assertThatCode(() -> dev.requireAllowed(URI.create("http://localhost:11434/v1"))).doesNotThrowAnyException();
        assertThatCode(() -> dev.requireAllowed(URI.create("https://localhost:11434/v1"))).doesNotThrowAnyException();

        assertThatThrownBy(() -> resolvingTo(true, "169.254.169.254").requireAllowed(URI.create("http://metadata.internal/v1")))
                .isInstanceOf(IllegalArgumentException.class);
        // Plain http is only for loopback, not for a private network address, even in development.
        assertThatThrownBy(() -> resolvingTo(true, "10.0.0.7").requireAllowed(URI.create("http://models.lan/v1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void credentialsQueryAndFragmentInTheUrlAreRefused() {
        AiEndpointGuard guard = resolvingTo(false, "93.184.216.34");
        assertThatThrownBy(() -> guard.requireAllowed(URI.create("https://user:pw@llm.example.com/v1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.requireAllowed(URI.create("https://llm.example.com/v1?key=1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.requireAllowed(URI.create("https://llm.example.com/v1#x"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnresolvableHostGivesAClearMessage() {
        AiEndpointGuard guard = new AiEndpointGuard(false, host -> {
            throw new IllegalArgumentException("unresolvable");
        });
        assertThatThrownBy(() -> guard.requireAllowed(URI.create("https://nope.example.invalid/v1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("couldn't be found");
    }

    @Test
    void theCallTimeCheckSurfacesAsAnAiException() {
        assertThatThrownBy(() -> resolvingTo(false, "10.0.0.5").requireAllowedForCall(URI.create("https://x.example.com/v1/chat")))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.code()).isEqualTo(AiErrorCode.ENDPOINT_NOT_ALLOWED));
    }
}
