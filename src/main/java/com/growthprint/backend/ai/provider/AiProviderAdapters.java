package com.growthprint.backend.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Executes provider calls: picks the adapter for the provider's API format, sends the HTTP request with the
 * configured timeout, retries at most once (timeouts, 429 and 5xx only) and normalises every failure to an
 * {@link AiErrorCode}. It never switches to another provider.
 * <p>
 * Logging records provider, HTTP status and attempt only — never keys, prompts or response bodies.
 */
@Component
public class AiProviderAdapters {

    private static final Logger log = LoggerFactory.getLogger(AiProviderAdapters.class);
    private static final int MAX_ATTEMPTS = 2;

    private final ObjectMapper json;
    private final Map<AiProviderType.ApiFormat, ProviderAdapter> adapters = new EnumMap<>(AiProviderType.ApiFormat.class);
    private final HttpClient http;

    public AiProviderAdapters(ObjectMapper json) {
        this.json = json;
        adapters.put(AiProviderType.ApiFormat.OPENAI_CHAT, new OpenAiChatAdapter(json));
        adapters.put(AiProviderType.ApiFormat.GEMINI, new GeminiAdapter(json));
        adapters.put(AiProviderType.ApiFormat.ANTHROPIC, new AnthropicAdapter(json));
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public AiCompletion complete(ProviderConnection connection, AiRequest request) {
        ProviderAdapter adapter = adapters.get(connection.type().format());
        JsonNode response = execute(connection, adapter.buildCompletion(connection, request));
        return adapter.parseCompletion(response).withSource(connection.type().name(), connection.model());
    }

    /** Model discovery, for providers that offer a listing endpoint. */
    public List<String> listModels(ProviderConnection connection) {
        ProviderAdapter adapter = adapters.get(connection.type().format());
        return adapter.parseModelList(execute(connection, adapter.buildModelList(connection)));
    }

    private JsonNode execute(ProviderConnection connection, ProviderAdapter.HttpCall call) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(call.uri())
                .timeout(connection.timeout())
                .header("Accept", "application/json");
        call.headers().forEach(builder::header);
        if (call.jsonBody() != null) {
            builder.header("Content-Type", "application/json")
                    .method(call.method(), HttpRequest.BodyPublishers.ofString(call.jsonBody(), StandardCharsets.UTF_8));
        } else {
            builder.method(call.method(), HttpRequest.BodyPublishers.noBody());
        }
        HttpRequest httpRequest = builder.build();

        AiException lastFailure = new AiException(AiErrorCode.PROVIDER_UNAVAILABLE);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return parseJson(response.body());
                }
                lastFailure = new AiException(mapStatus(status));
                log.warn("AI provider {} returned HTTP {} (attempt {}/{})", connection.type(), status, attempt, MAX_ATTEMPTS);
                if (!isRetryable(status)) {
                    throw lastFailure;
                }
            } catch (HttpTimeoutException e) {
                lastFailure = new AiException(AiErrorCode.TIMEOUT);
                log.warn("AI provider {} timed out after {} (attempt {}/{})", connection.type(), connection.timeout(), attempt, MAX_ATTEMPTS);
            } catch (ConnectException e) {
                lastFailure = new AiException(AiErrorCode.PROVIDER_UNAVAILABLE);
                log.warn("AI provider {} could not be reached (attempt {}/{})", connection.type(), attempt, MAX_ATTEMPTS);
            } catch (IOException e) {
                lastFailure = new AiException(AiErrorCode.PROVIDER_UNAVAILABLE);
                log.warn("AI provider {} connection failed: {} (attempt {}/{})", connection.type(),
                        e.getClass().getSimpleName(), attempt, MAX_ATTEMPTS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiException(AiErrorCode.PROVIDER_UNAVAILABLE);
            }
        }
        throw lastFailure;
    }

    private JsonNode parseJson(String body) {
        try {
            return json.readTree(body);
        } catch (IOException e) {
            throw new AiException(AiErrorCode.MALFORMED_RESPONSE);
        }
    }

    static AiErrorCode mapStatus(int status) {
        if (status == 401 || status == 403) {
            return AiErrorCode.INVALID_CREDENTIALS;
        }
        if (status == 404) {
            return AiErrorCode.UNSUPPORTED_MODEL;
        }
        if (status == 429) {
            return AiErrorCode.RATE_LIMITED;
        }
        if (status >= 500) {
            return AiErrorCode.PROVIDER_UNAVAILABLE;
        }
        return AiErrorCode.PROVIDER_REJECTED;
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }
}
