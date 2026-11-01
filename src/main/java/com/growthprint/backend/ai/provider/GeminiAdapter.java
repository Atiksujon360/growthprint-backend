package com.growthprint.backend.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini {@code generateContent} ({@code POST /v1beta/models/{model}:generateContent}).
 * The key travels in the {@code x-goog-api-key} header, never in the URL.
 */
final class GeminiAdapter implements ProviderAdapter {

    private final ObjectMapper json;

    GeminiAdapter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public HttpCall buildCompletion(ProviderConnection connection, AiRequest request) {
        ObjectNode body = json.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", request.systemPrompt());
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", request.userPrompt());
        ObjectNode config = body.putObject("generationConfig");
        config.put("maxOutputTokens", request.maxOutputTokens());
        if (request.jsonOutput()) {
            config.put("responseMimeType", "application/json");
        }
        String model = connection.model().startsWith("models/") ? connection.model().substring(7) : connection.model();
        URI uri = URI.create(ProviderAdapter.trimTrailingSlash(connection.baseUrl()) + "/v1beta/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8).replace("+", "%20") + ":generateContent");
        return new HttpCall("POST", uri, Map.of("x-goog-api-key", connection.apiKey()), body.toString());
    }

    @Override
    public AiCompletion parseCompletion(JsonNode response) {
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        StringBuilder text = new StringBuilder();
        parts.forEach(part -> {
            if (part.path("text").isTextual()) {
                text.append(part.path("text").asText());
            }
        });
        if (!parts.isArray() || text.isEmpty()) {
            throw new AiException(AiErrorCode.MALFORMED_RESPONSE);
        }
        JsonNode usage = response.path("usageMetadata");
        return new AiCompletion(text.toString(), OpenAiChatAdapter.intOrNull(usage, "promptTokenCount"),
                OpenAiChatAdapter.intOrNull(usage, "candidatesTokenCount"), null, null);
    }

    @Override
    public HttpCall buildModelList(ProviderConnection connection) {
        return new HttpCall("GET", URI.create(ProviderAdapter.trimTrailingSlash(connection.baseUrl()) + "/v1beta/models"),
                Map.of("x-goog-api-key", connection.apiKey()), null);
    }

    @Override
    public List<String> parseModelList(JsonNode response) {
        List<String> ids = new ArrayList<>();
        response.path("models").forEach(model -> {
            String name = model.path("name").asText("");
            if (!name.isEmpty()) {
                ids.add(name.startsWith("models/") ? name.substring(7) : name);
            }
        });
        return ids;
    }
}
