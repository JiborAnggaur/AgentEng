package com.mailagent.llm;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The assistant's reply for one chat() call: its content blocks plus the
 * Anthropic stop_reason ("tool_use" while the loop should keep going,
 * "end_turn"/others once it's done).
 */
public final class ChatResponse {

    private final List<ContentBlock> content;
    private final String stopReason;

    public ChatResponse(List<ContentBlock> content, String stopReason) {
        this.content = Collections.unmodifiableList(content);
        this.stopReason = stopReason;
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    public String getStopReason() {
        return stopReason;
    }

    public boolean hasToolUse() {
        return content.stream().anyMatch(b -> "tool_use".equals(b.getType()));
    }

    public List<ContentBlock> toolUseBlocks() {
        return content.stream().filter(b -> "tool_use".equals(b.getType())).collect(Collectors.toList());
    }

    public String textJoined() {
        return content.stream()
                .filter(b -> "text".equals(b.getType()))
                .map(ContentBlock::getText)
                .collect(Collectors.joining("\n"));
    }
}
