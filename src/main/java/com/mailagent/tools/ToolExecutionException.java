package com.mailagent.tools;

/**
 * Raised by a {@link Tool} for an expected, describable failure (bad/missing
 * argument, not-found lookup, etc). The message is safe to send back to the
 * LLM as the tool_result — never include raw email bodies or secrets here.
 */
public class ToolExecutionException extends Exception {

    public ToolExecutionException(String message) {
        super(message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
