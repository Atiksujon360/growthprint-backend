package com.growthprint.backend.ai.provider;

/**
 * Supported AI providers. Each one maps to an API format (see {@link ApiFormat}); new providers that speak
 * an existing format only need a new constant, a new format only needs a new {@link ProviderAdapter}.
 */
public enum AiProviderType {
    GEMINI("Google Gemini", ApiFormat.GEMINI, "https://generativelanguage.googleapis.com", false),
    OPENAI("OpenAI", ApiFormat.OPENAI_CHAT, "https://api.openai.com/v1", false),
    ANTHROPIC("Anthropic Claude", ApiFormat.ANTHROPIC, "https://api.anthropic.com", false),
    MISTRAL("Mistral AI", ApiFormat.OPENAI_CHAT, "https://api.mistral.ai/v1", false),
    DEEPSEEK("DeepSeek", ApiFormat.OPENAI_CHAT, "https://api.deepseek.com", false),
    OPENAI_COMPATIBLE("Custom OpenAI-compatible endpoint", ApiFormat.OPENAI_CHAT, null, true);

    public enum ApiFormat { OPENAI_CHAT, GEMINI, ANTHROPIC }

    private final String displayName;
    private final ApiFormat format;
    private final String defaultBaseUrl;
    private final boolean customBaseUrl;

    AiProviderType(String displayName, ApiFormat format, String defaultBaseUrl, boolean customBaseUrl) {
        this.displayName = displayName;
        this.format = format;
        this.defaultBaseUrl = defaultBaseUrl;
        this.customBaseUrl = customBaseUrl;
    }

    public String displayName() {
        return displayName;
    }

    public ApiFormat format() {
        return format;
    }

    /** Fixed endpoint for built-in providers; null for the custom endpoint (configured by the admin). */
    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    /** Only the custom provider accepts an admin-supplied base URL (built-ins are pinned to official hosts). */
    public boolean requiresCustomBaseUrl() {
        return customBaseUrl;
    }

    /** OpenAI's current chat models take max_completion_tokens; other OpenAI-style APIs take max_tokens. */
    public String openAiTokenLimitField() {
        return this == OPENAI ? "max_completion_tokens" : "max_tokens";
    }
}
