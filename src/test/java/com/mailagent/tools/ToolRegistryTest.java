package com.mailagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mailagent.support.Json;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ToolRegistryTest {

    @Test
    public void dispatchesToMatchingToolByName() {
        ToolRegistry registry = new ToolRegistry(Collections.singletonList(new EchoTool()));

        String result = registry.dispatch("echo", Json.MAPPER.createObjectNode());

        assertEquals("echo-ok", result);
    }

    @Test
    public void unknownToolNameReturnsErrorInsteadOfThrowing() {
        ToolRegistry registry = new ToolRegistry(Collections.singletonList(new EchoTool()));

        String result = registry.dispatch("does_not_exist", Json.MAPPER.createObjectNode());

        assertTrue(result.toLowerCase().contains("unknown"));
    }

    @Test
    public void toolExecutionExceptionIsSurfacedAsErrorResult() {
        ToolRegistry registry = new ToolRegistry(Collections.singletonList(new FailingTool()));

        String result = registry.dispatch("failing", Json.MAPPER.createObjectNode());

        assertTrue(result.contains("bad arg"));
    }

    @Test
    public void unexpectedRuntimeExceptionDoesNotCrashTheLoop() {
        ToolRegistry registry = new ToolRegistry(Collections.singletonList(new CrashingTool()));

        String result = registry.dispatch("crashing", Json.MAPPER.createObjectNode());

        assertTrue(result.toLowerCase().contains("error"));
    }

    @Test
    public void toolsReturnsAllRegisteredTools() {
        ToolRegistry registry = new ToolRegistry(Arrays.asList(new EchoTool(), new FailingTool()));

        List<Tool> tools = registry.tools();

        assertEquals(2, tools.size());
    }

    private static class EchoTool implements Tool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "echo";
        }

        @Override
        public ObjectNode inputSchema() {
            return Json.MAPPER.createObjectNode().put("type", "object");
        }

        @Override
        public String execute(JsonNode args) {
            return "echo-ok";
        }
    }

    private static class FailingTool implements Tool {
        @Override
        public String name() {
            return "failing";
        }

        @Override
        public String description() {
            return "failing";
        }

        @Override
        public ObjectNode inputSchema() {
            return Json.MAPPER.createObjectNode().put("type", "object");
        }

        @Override
        public String execute(JsonNode args) throws ToolExecutionException {
            throw new ToolExecutionException("bad arg");
        }
    }

    private static class CrashingTool implements Tool {
        @Override
        public String name() {
            return "crashing";
        }

        @Override
        public String description() {
            return "crashing";
        }

        @Override
        public ObjectNode inputSchema() {
            return Json.MAPPER.createObjectNode().put("type", "object");
        }

        @Override
        public String execute(JsonNode args) {
            throw new RuntimeException("boom");
        }
    }
}
