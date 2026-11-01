package com.growthprint.backend.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * One provider API format. Adapters only build the HTTP request and parse the answer; HTTP execution,
 * the single retry and error normalisation are shared in {@link AiProviderAdapters}.
 */
interface ProviderAdapter {

    /** The HTTP request for a generation call. */
    HttpCall buildCompletion(ProviderConnection connection, AiRequest request);

    /** Extracts the generated text and reported token usage; throws MALFORMED_RESPONSE if absent. */
    AiCompletion parseCompletion(JsonNode response);

    /** The HTTP request that lists models available to this key. */
    HttpCall buildModelList(ProviderConnection connection);

    /** Extracts model identifiers from a model-list response. */
    List<String> parseModelList(JsonNode response);

    record HttpCall(String method, URI uri, Map<String, String> headers, String jsonBody) {
        @Override
        public String toString() {
            return "HttpCall[" + method + " " + uri + "]";
        }
    }

    static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
