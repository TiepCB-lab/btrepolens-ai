package com.btrepolens.chat;

import com.btrepolens.common.AppException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.http.HttpStatus;

final class AiRequestErrors {
    private AiRequestErrors() {}

    static AppException translate(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OpenAIServiceException service) {
                return switch (service.statusCode()) {
                    case 401, 403 -> new AppException(HttpStatus.BAD_GATEWAY, "AI_AUTH_FAILED",
                        "The AI provider rejected access. Check the API key and model permissions.");
                    case 404 -> new AppException(HttpStatus.BAD_GATEWAY, "AI_MODEL_UNAVAILABLE",
                        "The AI model or endpoint was not found. Check the provider configuration.");
                    case 429 -> new AppException(HttpStatus.TOO_MANY_REQUESTS, "AI_LIMIT_REACHED",
                        "The AI provider reported a rate or usage limit. Wait before retrying and check your provider quota.");
                    case 500, 502, 503, 504 -> new AppException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_UNAVAILABLE",
                        "The AI provider is temporarily unavailable. Try again later.");
                    default -> generic();
                };
            }
            if (cause instanceof OpenAIIoException) {
                return new AppException(HttpStatus.BAD_GATEWAY, "AI_CONNECTION_FAILED",
                    "Could not connect to the AI provider. Check your connection and try again.");
            }
        }
        return generic();
    }

    private static AppException generic() {
        return new AppException(HttpStatus.BAD_GATEWAY, "AI_REQUEST_FAILED",
            "The AI request failed. Check provider configuration and try again.");
    }
}
