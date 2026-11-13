package com.btrepolens.chat;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiRequestErrorsTest {
    @Test void translatesProviderStatusesWithoutExposingRawErrors() {
        int[] statuses = {401, 403, 404, 429, 500, 503, 400};
        String[] codes = {"AI_AUTH_FAILED", "AI_AUTH_FAILED", "AI_MODEL_UNAVAILABLE", "AI_LIMIT_REACHED",
            "AI_PROVIDER_UNAVAILABLE", "AI_PROVIDER_UNAVAILABLE", "AI_REQUEST_FAILED"};
        for (int i = 0; i < statuses.length; i++) {
            var providerError = mock(OpenAIServiceException.class);
            when(providerError.statusCode()).thenReturn(statuses[i]);
            var result = AiRequestErrors.translate(new RuntimeException("secret provider content", providerError));
            assertEquals(codes[i], result.code());
            assertEquals(statuses[i] == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY, result.status());
            assertFalse(result.getMessage().contains("secret provider content"));
        }
    }

    @Test void handlesNetworkAndUnexpectedErrorsSafely() {
        assertEquals("AI_CONNECTION_FAILED", AiRequestErrors.translate(new OpenAIIoException("secret")).code());
        var unexpected = AiRequestErrors.translate(new IllegalStateException("secret"));
        assertEquals("AI_REQUEST_FAILED", unexpected.code());
        assertFalse(unexpected.getMessage().contains("secret"));
    }
}
