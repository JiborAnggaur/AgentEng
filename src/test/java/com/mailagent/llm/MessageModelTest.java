package com.mailagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.mailagent.support.Json;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageModelTest {

    @Test
    public void textBlockCarriesItsText() {
        ContentBlock block = ContentBlock.text("hello");

        assertEquals("text", block.getType());
        assertEquals("hello", block.getText());
    }

    @Test
    public void toolUseBlockCarriesIdNameAndInput() {
        JsonNode input = Json.MAPPER.createObjectNode().put("query", "milk");
        ContentBlock block = ContentBlock.toolUse("toolu_1", "find_items", input);

        assertEquals("tool_use", block.getType());
        assertEquals("toolu_1", block.getToolUseId());
        assertEquals("find_items", block.getToolName());
        assertEquals("milk", block.getToolInput().get("query").asText());
    }

    @Test
    public void toolResultBlockCarriesIdAndContent() {
        ContentBlock block = ContentBlock.toolResult("toolu_1", "Found 0 reminders.");

        assertEquals("tool_result", block.getType());
        assertEquals("toolu_1", block.getToolUseId());
        assertEquals("Found 0 reminders.", block.getToolResultContent());
    }

    @Test
    public void chatMessageFactoriesSetRole() {
        ChatMessage user = ChatMessage.user(Collections.singletonList(ContentBlock.text("hi")));
        ChatMessage assistant = ChatMessage.assistant(Collections.singletonList(ContentBlock.text("hi back")));

        assertEquals("user", user.getRole());
        assertEquals("assistant", assistant.getRole());
    }

    @Test
    public void chatResponseReportsToolUsePresence() {
        ChatResponse withTool = new ChatResponse(
                Collections.singletonList(ContentBlock.toolUse("id", "current_datetime", Json.MAPPER.createObjectNode())),
                "tool_use");
        ChatResponse withoutTool = new ChatResponse(
                Collections.singletonList(ContentBlock.text("done")), "end_turn");

        assertTrue(withTool.hasToolUse());
        assertFalse(withoutTool.hasToolUse());
        assertEquals(1, withTool.toolUseBlocks().size());
        assertEquals("done", withoutTool.textJoined());
    }
}
