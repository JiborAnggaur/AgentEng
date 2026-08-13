package com.mailagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A tool exposed to the LLM via tool-calling. Implementations must be
 * deterministic and side-effect-free to reason about in tests where possible
 * (see {@link CurrentDatetimeTool}'s injectable Clock).
 */
public interface Tool {

    String name();

    String description();

    /**
     * JSON Schema (draft-07 subset) describing the tool's input, in the shape
     * the Anthropic Messages API expects under tools[].input_schema.
     */
    ObjectNode inputSchema();

    /**
     * Executes the tool. Implementations should throw {@link ToolExecutionException}
     * for expected failures (bad argument, not found, ...) rather than letting
     * runtime exceptions escape — the tool loop treats both the same way (an
     * error tool_result back to the model) but a typed exception keeps the
     * error message intentional.
     */
    String execute(JsonNode args) throws ToolExecutionException;
}
