package com.mailagent.llm;

/**
 * Unchecked wrapper for LLM-transport failures (non-2xx HTTP, network error,
 * unparseable response) so MailAgent's generic RuntimeException catch turns
 * any of them into a graceful fallback reply instead of a crash.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
