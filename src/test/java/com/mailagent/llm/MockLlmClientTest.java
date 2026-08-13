package com.mailagent.llm;

import com.mailagent.support.Json;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class MockLlmClientTest {

    @Test
    public void returnsQueuedResponsesInOrder() {
        ChatResponse first = new ChatResponse(Collections.singletonList(ContentBlock.text("first")), "end_turn");
        ChatResponse second = new ChatResponse(Collections.singletonList(ContentBlock.text("second")), "end_turn");
        MockLlmClient client = new MockLlmClient(Arrays.asList(first, second));

        List<ChatMessage> messages = Collections.singletonList(ChatMessage.user(Collections.singletonList(ContentBlock.text("hi"))));

        assertSame(first, client.chat(messages, Collections.emptyList()));
        assertSame(second, client.chat(messages, Collections.emptyList()));
    }

    @Test
    public void recordsEachRequestForLaterAssertions() {
        ChatResponse response = new ChatResponse(Collections.singletonList(ContentBlock.text("ok")), "end_turn");
        MockLlmClient client = new MockLlmClient(Collections.singletonList(response));
        List<ChatMessage> messages = Collections.singletonList(ChatMessage.user(Collections.singletonList(ContentBlock.text("hi"))));
        List<ToolSpec> tools = Collections.singletonList(new ToolSpec("current_datetime", "desc", Json.MAPPER.createObjectNode()));

        client.chat(messages, tools);

        assertEquals(1, client.requests().size());
        assertEquals(messages, client.requests().get(0).getMessages());
        assertEquals(tools, client.requests().get(0).getTools());
    }

    @Test(expected = IllegalStateException.class)
    public void throwsWhenQueueIsExhausted() {
        MockLlmClient client = new MockLlmClient(Collections.emptyList());

        client.chat(Collections.emptyList(), Collections.emptyList());
    }
}
