package com.mailagent.llm;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One turn in the conversation sent to/received from the LLM: a role plus a
 * list of content blocks (Anthropic messages are multi-block, not flat text).
 */
public final class ChatMessage {

    private final String role;
    private final List<ContentBlock> content;

    private ChatMessage(String role, List<ContentBlock> content) {
        this.role = role;
        this.content = Collections.unmodifiableList(content);
    }

    public static ChatMessage user(List<ContentBlock> content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(List<ContentBlock> content) {
        return new ChatMessage("assistant", content);
    }

    public String getRole() {
        return role;
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatMessage)) {
            return false;
        }
        ChatMessage other = (ChatMessage) o;
        return Objects.equals(role, other.role) && Objects.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content);
    }
}
