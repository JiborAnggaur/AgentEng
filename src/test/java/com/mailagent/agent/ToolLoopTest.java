package com.mailagent.agent;

import com.mailagent.llm.ChatMessage;
import com.mailagent.llm.ChatResponse;
import com.mailagent.llm.ContentBlock;
import com.mailagent.llm.MockLlmClient;
import com.mailagent.support.Json;
import com.mailagent.tools.CurrentDatetimeTool;
import com.mailagent.tools.Tool;
import com.mailagent.tools.ToolRegistry;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ToolLoopTest {

    @Test
    public void returnsFinalTextWhenModelAnswersImmediately() {
        MockLlmClient llm = new MockLlmClient(Collections.singletonList(
                new ChatResponse(Collections.singletonList(ContentBlock.text("Hello!")), "end_turn")));
        ToolRegistry registry = new ToolRegistry(Collections.emptyList());
        ToolLoop loop = new ToolLoop(llm, registry, 6);

        String result = loop.run("system", "hi");

        assertEquals("Hello!", result);
        assertEquals(1, llm.requests().size());
    }

    @Test
    public void callsToolThenReturnsFinalAnswer() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-13T09:15:00Z"), ZoneOffset.UTC);
        Tool tool = new CurrentDatetimeTool(fixed);
        ToolRegistry registry = new ToolRegistry(Collections.singletonList(tool));

        ChatResponse toolUseResponse = new ChatResponse(
                Collections.singletonList(ContentBlock.toolUse("toolu_1", "current_datetime", Json.MAPPER.createObjectNode())),
                "tool_use");
        ChatResponse finalResponse = new ChatResponse(
                Collections.singletonList(ContentBlock.text("It is 2026-08-13T09:15:00Z")), "end_turn");
        MockLlmClient llm = new MockLlmClient(Arrays.asList(toolUseResponse, finalResponse));

        ToolLoop loop = new ToolLoop(llm, registry, 6);

        String result = loop.run("system", "what time is it?");

        assertEquals("It is 2026-08-13T09:15:00Z", result);
        assertEquals(2, llm.requests().size());

        ChatMessage secondRequestLastMessage = llm.requests().get(1).getMessages()
                .get(llm.requests().get(1).getMessages().size() - 1);
        assertEquals("user", secondRequestLastMessage.getRole());
        ContentBlock toolResult = secondRequestLastMessage.getContent().get(0);
        assertEquals("tool_result", toolResult.getType());
        assertEquals("toolu_1", toolResult.getToolUseId());
        assertEquals("2026-08-13T09:15:00Z", toolResult.getToolResultContent());
    }

    @Test
    public void unknownToolDoesNotCrashTheLoop() {
        ToolRegistry registry = new ToolRegistry(Collections.emptyList());

        ChatResponse hallucinatedToolCall = new ChatResponse(
                Collections.singletonList(ContentBlock.toolUse("toolu_1", "does_not_exist", Json.MAPPER.createObjectNode())),
                "tool_use");
        ChatResponse finalResponse = new ChatResponse(
                Collections.singletonList(ContentBlock.text("Sorry, something went wrong.")), "end_turn");
        MockLlmClient llm = new MockLlmClient(Arrays.asList(hallucinatedToolCall, finalResponse));

        ToolLoop loop = new ToolLoop(llm, registry, 6);

        String result = loop.run("system", "do the impossible");

        assertEquals("Sorry, something went wrong.", result);
    }

    @Test
    public void stopsAtMaxStepsWithoutCrashingOrLoopingForever() {
        ToolRegistry registry = new ToolRegistry(Collections.emptyList());
        ChatResponse alwaysToolUse = new ChatResponse(
                Collections.singletonList(ContentBlock.toolUse("toolu_1", "current_datetime", Json.MAPPER.createObjectNode())),
                "tool_use");
        MockLlmClient llm = new MockLlmClient(Arrays.asList(
                alwaysToolUse, alwaysToolUse, alwaysToolUse, alwaysToolUse, alwaysToolUse, alwaysToolUse));

        ToolLoop loop = new ToolLoop(llm, registry, 3);

        String result = loop.run("system", "loop forever please");

        assertEquals(3, llm.requests().size());
        assertTrue(result.length() > 0);
    }
}
