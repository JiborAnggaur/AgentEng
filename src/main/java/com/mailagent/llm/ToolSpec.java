package com.mailagent.llm;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mailagent.tools.Tool;

import java.util.Objects;

/**
 * A tool description sent to the LLM in the tools[] request field, matching
 * the Anthropic Messages API shape ({@code name}/{@code description}/{@code input_schema}).
 */
public final class ToolSpec {

    private final String name;
    private final String description;
    private final ObjectNode inputSchema;

    public ToolSpec(String name, String description, ObjectNode inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public static ToolSpec from(Tool tool) {
        return new ToolSpec(tool.name(), tool.description(), tool.inputSchema());
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ObjectNode getInputSchema() {
        return inputSchema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ToolSpec)) {
            return false;
        }
        ToolSpec other = (ToolSpec) o;
        return Objects.equals(name, other.name)
                && Objects.equals(description, other.description)
                && Objects.equals(inputSchema, other.inputSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, inputSchema);
    }
}
