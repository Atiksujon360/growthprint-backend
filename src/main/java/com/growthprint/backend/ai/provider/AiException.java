package com.growthprint.backend.ai.provider;

/**
 * Normalised AI failure. The message is always one of the fixed, safe texts in {@link AiErrorCode}:
 * provider response bodies are never copied into it (they can echo keys or prompt content).
 */
public class AiException extends RuntimeException {

    private final AiErrorCode code;

    public AiException(AiErrorCode code) {
        super(code.message());
        this.code = code;
    }

    public AiException(AiErrorCode code, Throwable cause) {
        super(code.message(), cause);
        this.code = code;
    }

    public AiErrorCode code() {
        return code;
    }
}
