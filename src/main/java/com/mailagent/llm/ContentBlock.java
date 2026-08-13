package com.mailagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.mailagent.support.Json;

/**
 * One content block within a {@link ChatMessage}, mirroring the Anthropic
 * Messages API's content block union (text / tool_use / tool_result). Kept as
 * a single tagged class rather than a class hierarchy since the set of shapes
 * is small, fixed, and each is only a few fields.
 */
public final class ContentBlock {

    private final String type;
    private final String text;
    private final String toolUseId;
    private final String toolName;
    private final JsonNode toolInput;
    private final String toolResultContent;

    private ContentBlock(String type, String text, String toolUseId, String toolName,
                          JsonNode toolInput, String toolResultContent) {
        this.type = type;
        this.text = text;
        this.toolUseId = toolUseId;
        this.toolName = toolName;
        this.toolInput = toolInput;
        this.toolResultContent = toolResultContent;
    }

    public static ContentBlock text(String text) {
        return new ContentBlock("text", text, null, null, null, null);
    }

    public static ContentBlock toolUse(String toolUseId, String toolName, JsonNode toolInput) {
        return new ContentBlock("tool_use", null, toolUseId, toolName, toolInput, null);
    }

    public static ContentBlock toolResult(String toolUseId, String content) {
        return new ContentBlock("tool_result", null, toolUseId, null, null, content);
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getToolUseId() {
        return toolUseId;
    }

    public String getToolName() {
        return toolName;
    }

    public JsonNode getToolInput() {
        return toolInput == null ? Json.MAPPER.createObjectNode() : toolInput;
    }

    public String getToolResultContent() {
        return toolResultContent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContentBlock)) {
            return false;
        }
        ContentBlock other = (ContentBlock) o;
        return java.util.Objects.equals(type, other.type)
                && java.util.Objects.equals(text, other.text)
                && java.util.Objects.equals(toolUseId, other.toolUseId)
                && java.util.Objects.equals(toolName, other.toolName)
                && java.util.Objects.equals(toolInput, other.toolInput)
                && java.util.Objects.equals(toolResultContent, other.toolResultContent);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, text, toolUseId, toolName, toolInput, toolResultContent);
    }
}
