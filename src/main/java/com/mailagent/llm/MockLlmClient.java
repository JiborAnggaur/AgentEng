package com.mailagent.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Test double for {@link LlmClient}: returns canned responses in order and
 * records every request so tests can assert what the tool loop sent up.
 */
public class MockLlmClient implements LlmClient {

    private final Deque<ChatResponse> queued;
    private final List<Recorded> requests = new ArrayList<>();

    public MockLlmClient(List<ChatResponse> responses) {
        this.queued = new ArrayDeque<>(responses);
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools) {
        requests.add(new Recorded(messages, tools));
        if (queued.isEmpty()) {
            throw new IllegalStateException("MockLlmClient queue exhausted — add more canned responses");
        }
        return queued.poll();
    }

    public List<Recorded> requests() {
        return requests;
    }

    public static final class Recorded {
        private final List<ChatMessage> messages;
        private final List<ToolSpec> tools;

        Recorded(List<ChatMessage> messages, List<ToolSpec> tools) {
            this.messages = messages;
            this.tools = tools;
        }

        public List<ChatMessage> getMessages() {
            return messages;
        }

        public List<ToolSpec> getTools() {
            return tools;
        }
    }
}
