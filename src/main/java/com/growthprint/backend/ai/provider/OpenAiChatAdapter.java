package com.growthprint.backend.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-style Chat Completions ({@code POST {base}/chat/completions}, {@code Authorization: Bearer}).
 * Used by OpenAI, Mistral, DeepSeek and custom OpenAI-compatible endpoints.
 */
final class OpenAiChatAdapter implements ProviderAdapter {

    private final ObjectMapper json;

    OpenAiChatAdapter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public HttpCall buildCompletion(ProviderConnection connection, AiRequest request) {
        ObjectNode body = json.createObjectNode();
        body.put("model", connection.model());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemPrompt());
        messages.addObject().put("role", "user").put("content", request.userPrompt());
        body.put(connection.type().openAiTokenLimitField(), request.maxOutputTokens());
        if (request.jsonOutput()) {
            body.putObject("response_format").put("type", "json_object");
        }
        return new HttpCall("POST", URI.create(ProviderAdapter.trimTrailingSlash(connection.baseUrl()) + "/chat/completions"),
                Map.of("Authorization", "Bearer " + connection.apiKey()), body.toString());
    }

    @Override
    public AiCompletion parseCompletion(JsonNode response) {
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new AiException(AiErrorCode.MALFORMED_RESPONSE);
        }
        JsonNode usage = response.path("usage");
        return new AiCompletion(content.asText(),
                intOrNull(usage, "prompt_tokens"), intOrNull(usage, "completion_tokens"), null, null);
    }

    @Override
    public HttpCall buildModelList(ProviderConnection connection) {
        return new HttpCall("GET", URI.create(ProviderAdapter.trimTrailingSlash(connection.baseUrl()) + "/models"),
                Map.of("Authorization", "Bearer " + connection.apiKey()), null);
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

    static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }
}
