package com.growthprint.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthprint.backend.ai.provider.AiCompletion;
import com.growthprint.backend.ai.provider.AiErrorCode;
import com.growthprint.backend.ai.provider.AiException;
import com.growthprint.backend.ai.provider.AiProviderAdapters;
import com.growthprint.backend.ai.provider.AiProviderType;
import com.growthprint.backend.ai.provider.AiRequest;
import com.growthprint.backend.ai.provider.ProviderConnection;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wire format and error mapping of every provider adapter, against a local fake server.
 * No real provider is called and no real key is used.
 */
class AiProviderAdaptersTest {

    private static final String KEY = "sk-test-not-a-real-key-123456";
    private final ObjectMapper json = new ObjectMapper();
    private final AiProviderAdapters adapters = new AiProviderAdapters(json);
    private final AiRequest request = new AiRequest("You are careful.", "Classify this.", true, 300);

    private ProviderConnection connection(AiProviderType type, FakeProviderServer server, String model) {
        return new ProviderConnection(type, server.baseUrl(), KEY, model, Duration.ofSeconds(2));
    }

    // ---- OpenAI-style chat completions (OpenAI, Mistral, DeepSeek, custom compatible) --------------

    @Test
    void openAiSendsChatCompletionWithBearerKeyJsonModeAndCompletionTokenLimit() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(FakeProviderServer.Reply.ok("""
                {"choices":[{"message":{"role":"assistant","content":"{\\"ok\\":true}"}}],
                 "usage":{"prompt_tokens":12,"completion_tokens":5}}"""))) {

            AiCompletion completion = adapters.complete(connection(AiProviderType.OPENAI, server, "gpt-test"), request);

            FakeProviderServer.RecordedRequest sent = server.requests().get(0);
            assertThat(sent.method()).isEqualTo("POST");
            assertThat(sent.path()).isEqualTo("/chat/completions");
            assertThat(sent.header("Authorization")).isEqualTo("Bearer " + KEY);
            JsonNode body = json.readTree(sent.body());
            assertThat(body.path("model").asText()).isEqualTo("gpt-test");
            assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
            assertThat(body.path("messages").get(1).path("content").asText()).isEqualTo("Classify this.");
            assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
            assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(300);
            assertThat(body.has("max_tokens")).isFalse();

            assertThat(completion.text()).isEqualTo("{\"ok\":true}");
            assertThat(completion.inputTokens()).isEqualTo(12);
            assertThat(completion.outputTokens()).isEqualTo(5);
        }
    }

    @Test
    void mistralDeepSeekAndCustomUseMaxTokens() throws Exception {
        for (AiProviderType type : List.of(AiProviderType.MISTRAL, AiProviderType.DEEPSEEK, AiProviderType.OPENAI_COMPATIBLE)) {
            try (FakeProviderServer server = new FakeProviderServer(FakeProviderServer.Reply.ok(
                    "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}"))) {
                adapters.complete(connection(type, server, "m"), request);
                JsonNode body = json.readTree(server.requests().get(0).body());
                assertThat(body.path("max_tokens").asInt()).as(type.name()).isEqualTo(300);
                assertThat(server.requests().get(0).path()).as(type.name()).isEqualTo("/chat/completions");
            }
        }
    }

    // ---- Gemini ------------------------------------------------------------------------------

    @Test
    void geminiUsesGenerateContentWithGoogleApiKeyHeaderAndSystemInstruction() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(FakeProviderServer.Reply.ok("""
                {"candidates":[{"content":{"parts":[{"text":"{\\"ok\\":1}"}]}}],
                 "usageMetadata":{"promptTokenCount":7,"candidatesTokenCount":3}}"""))) {

            AiCompletion completion = adapters.complete(connection(AiProviderType.GEMINI, server, "gemini-test"), request);

            FakeProviderServer.RecordedRequest sent = server.requests().get(0);
            assertThat(sent.path()).isEqualTo("/v1beta/models/gemini-test:generateContent");
            assertThat(sent.header("x-goog-api-key")).isEqualTo(KEY);
            assertThat(sent.query()).as("key must not be sent in the URL").isNull();
            JsonNode body = json.readTree(sent.body());
            assertThat(body.path("systemInstruction").path("parts").get(0).path("text").asText()).isEqualTo("You are careful.");
            assertThat(body.path("contents").get(0).path("parts").get(0).path("text").asText()).isEqualTo("Classify this.");
            assertThat(body.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
            assertThat(body.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(300);

            assertThat(completion.text()).isEqualTo("{\"ok\":1}");
            assertThat(completion.inputTokens()).isEqualTo(7);
            assertThat(completion.outputTokens()).isEqualTo(3);
        }
    }

    // ---- Anthropic ---------------------------------------------------------------------------

    @Test
    void anthropicUsesMessagesApiWithApiKeyVersionHeaderAndTopLevelSystem() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(FakeProviderServer.Reply.ok("""
                {"content":[{"type":"text","text":"{\\"ok\\":2}"}],
                 "usage":{"input_tokens":9,"output_tokens":4}}"""))) {

            AiCompletion completion = adapters.complete(connection(AiProviderType.ANTHROPIC, server, "claude-test"), request);

            FakeProviderServer.RecordedRequest sent = server.requests().get(0);
            assertThat(sent.path()).isEqualTo("/v1/messages");
            assertThat(sent.header("x-api-key")).isEqualTo(KEY);
            assertThat(sent.header("anthropic-version")).isNotBlank();
            JsonNode body = json.readTree(sent.body());
            assertThat(body.path("system").asText()).contains("You are careful.");
            assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
            assertThat(body.path("max_tokens").asInt()).isEqualTo(300);

            assertThat(completion.text()).isEqualTo("{\"ok\":2}");
            assertThat(completion.inputTokens()).isEqualTo(9);
        }
    }

    // ---- Error normalisation (shared by all adapters) ----------------------------------------

    @Test
    void invalidKeyIsReportedAsInvalidCredentialsWithoutLeakingTheKey() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(
                FakeProviderServer.Reply.status(401, "{\"error\":{\"message\":\"Incorrect API key provided: " + KEY + "\"}}"))) {
            assertThatThrownBy(() -> adapters.complete(connection(AiProviderType.OPENAI, server, "m"), request))
                    .isInstanceOf(AiException.class)
                    .satisfies(e -> {
                        assertThat(((AiException) e).code()).isEqualTo(AiErrorCode.INVALID_CREDENTIALS);
                        assertThat(e.getMessage()).doesNotContain(KEY);
                    });
            assertThat(server.requests()).hasSize(1); // never retried
        }
    }

    @Test
    void unknownModelIsReportedAsUnsupportedModel() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(
                FakeProviderServer.Reply.status(404, "{\"error\":{\"message\":\"model not found\"}}"))) {
            assertThatThrownBy(() -> adapters.complete(connection(AiProviderType.GEMINI, server, "nope"), request))
                    .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.code()).isEqualTo(AiErrorCode.UNSUPPORTED_MODEL));
        }
    }

    @Test
    void rateLimitIsRetriedExactlyOnceThenReported() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(
                FakeProviderServer.Reply.status(429, "{}"), FakeProviderServer.Reply.status(429, "{}"),
                FakeProviderServer.Reply.status(429, "{}"))) {
            assertThatThrownBy(() -> adapters.complete(connection(AiProviderType.MISTRAL, server, "m"), request))
                    .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.code()).isEqualTo(AiErrorCode.RATE_LIMITED));
            assertThat(server.requests()).hasSize(2);
        }
    }

    @Test
    void transientServerErrorSucceedsOnTheSingleRetry() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(
                FakeProviderServer.Reply.status(503, "{}"),
                FakeProviderServer.Reply.ok("{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}"))) {
            AiCompletion completion = adapters.complete(connection(AiProviderType.DEEPSEEK, server, "m"), request);
            assertThat(completion.text()).isEqualTo("{}");
            assertThat(server.requests()).hasSize(2);
        }
    }

    @Test
    void persistentServerErrorIsProviderUnavailable() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(FakeProviderServer.Reply.status(500, "boom"))) {
            assertThatThrownBy(() -> adapters.complete(connection(AiProviderType.ANTHROPIC, server, "m"), request))
                    .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.code()).isEqualTo(AiErrorCode.PROVIDER_UNAVAILABLE));
            assertThat(server.requests()).hasSize(2);
        }
    }

    @Test
    void slowProviderTimesOut() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(FakeProviderServer.Reply.slow(1_500))) {
            ProviderConnection quick = new ProviderConnection(AiProviderType.OPENAI, server.baseUrl(), KEY, "m", Duration.ofMillis(300));
            assertThatThrownBy(() -> adapters.complete(quick, request))
                    .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.code()).isEqualTo(AiErrorCode.TIMEOUT));
        }
    }

    @Test
    void malformedProviderResponseIsReportedAsSuch() throws Exception {
        try (FakeProviderServer server = new FakeProviderServer(FakeProviderServer.Reply.ok("{\"unexpected\":true}"))) {
            assertThatThrownBy(() -> adapters.complete(connection(AiProviderType.OPENAI, server, "m"), request))
                    .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.code()).isEqualTo(AiErrorCode.MALFORMED_RESPONSE));
        }
    }

    @Test
    void connectionDetailsNeverPrintTheKey() {
        ProviderConnection connection = new ProviderConnection(AiProviderType.OPENAI, "https://x", KEY, "m", Duration.ofSeconds(1));
        assertThat(connection.toString()).doesNotContain(KEY);
    }
}
