package com.growthprint.backend.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API ({@code POST /v1/messages}, {@code x-api-key} + {@code anthropic-version}).
 * The system prompt is a top-level field. There is no JSON mode, so the JSON requirement is stated in the
 * system prompt and the caller validates the output.
 */
final class AnthropicAdapter implements ProviderAdapter {

    /** Stable API version documented for the Messages API. */
    static final String API_VERSION = "2023-06-01";

    private final ObjectMapper json;

    AnthropicAdapter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public HttpCall buildCompletion(ProviderConnection connection, AiRequest request) {
        ObjectNode body = json.createObjectNode();
        body.put("model", connection.model());
        body.put("max_tokens", request.maxOutputTokens());
        body.put("system", request.jsonOutput()
                ? request.systemPrompt() + "\n\nRespond with a single JSON object only, without any other text."
                : request.systemPrompt());
        body.putArray("messages").addObject().put("role", "user").put("content", request.userPrompt());
        return new HttpCall("POST", URI.create(ProviderAdapter.trimTrailingSlash(connection.baseUrl()) + "/v1/messages"),
                headers(connection), body.toString());
    }

    @Override
    public AiCompletion parseCompletion(JsonNode response) {
        StringBuilder text = new StringBuilder();
        response.path("content").forEach(block -> {
            if ("text".equals(block.path("type").asText()) && block.path("text").isTextual()) {
                text.append(block.path("text").asText());
            }
        });
        if (text.isEmpty()) {
            throw new AiException(AiErrorCode.MALFORMED_RESPONSE);
        }
        JsonNode usage = response.path("usage");
        return new AiCompletion(text.toString(), OpenAiChatAdapter.intOrNull(usage, "input_tokens"),
                OpenAiChatAdapter.intOrNull(usage, "output_tokens"), null, null);
    }

    @Override
    public HttpCall buildModelList(ProviderConnection connection) {
        return new HttpCall("GET", URI.create(ProviderAdapter.trimTrailingSlash(connection.baseUrl()) + "/v1/models"),
                headers(connection), null);
    }

    @Override
    public List<String> parseModelList(JsonNode response) {
        List<String> ids = new ArrayList<>();
        response.path("data").forEach(model -> {
            if (model.path("id").isTextual()) {
                ids.add(model.path("id").asText());
            }
        });
        return ids;
    }

    private static Map<String, String> headers(ProviderConnection connection) {
        return Map.of("x-api-key", connection.apiKey(), "anthropic-version", API_VERSION);
    }
}
