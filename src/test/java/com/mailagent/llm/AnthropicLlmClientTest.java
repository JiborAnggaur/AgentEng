package com.mailagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Exercises the real Anthropic Messages API wire format against a
 * MockWebServer instance — no network, no real API key, deterministic and
 * fast enough to run in `mvn test` on any machine.
 */
public class AnthropicLlmClientTest {

    private MockWebServer server;

    @Before
    public void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void stopServer() throws Exception {
        server.shutdown();
    }

    @Test
    public void sendsCorrectlyShapedRequest() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}],\"stop_reason\":\"end_turn\"}"));

        AnthropicLlmClient client = new AnthropicLlmClient(
                new OkHttpClient(), server.url("/v1/messages").toString(), "claude-sonnet-5", "test-api-key");

        List<ChatMessage> messages = Collections.singletonList(
                ChatMessage.user(Collections.singletonList(ContentBlock.text("Hello"))));
        List<ToolSpec> tools = Collections.singletonList(
                ToolSpec.from(new com.mailagent.tools.CurrentDatetimeTool(java.time.Clock.systemUTC())));

        client.chat(messages, tools);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/messages", request.getPath());
        assertEquals("test-api-key", request.getHeader("x-api-key"));
        assertEquals("2023-06-01", request.getHeader("anthropic-version"));

        JsonNode body = com.mailagent.support.Json.MAPPER.readTree(request.getBody().readUtf8());
        assertEquals("claude-sonnet-5", body.get("model").asText());
        assertEquals("user", body.get("messages").get(0).get("role").asText());
        assertEquals("Hello", body.get("messages").get(0).get("content").get(0).get("text").asText());
        assertEquals("current_datetime", body.get("tools").get(0).get("name").asText());
    }

    @Test
    public void parsesToolUseResponse() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"current_datetime\",\"input\":{}}],"
                        + "\"stop_reason\":\"tool_use\"}"));

        AnthropicLlmClient client = new AnthropicLlmClient(
                new OkHttpClient(), server.url("/v1/messages").toString(), "claude-sonnet-5", "test-api-key");

        ChatResponse response = client.chat(
                Collections.singletonList(ChatMessage.user(Collections.singletonList(ContentBlock.text("What time is it?")))),
                Collections.emptyList());

        assertTrue(response.hasToolUse());
        assertEquals("tool_use", response.getStopReason());
        ContentBlock toolUse = response.toolUseBlocks().get(0);
        assertEquals("toolu_1", toolUse.getToolUseId());
        assertEquals("current_datetime", toolUse.getToolName());
    }

    @Test
    public void parsesTextOnlyResponse() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"content\":[{\"type\":\"text\",\"text\":\"Today is Thursday.\"}],\"stop_reason\":\"end_turn\"}"));

        AnthropicLlmClient client = new AnthropicLlmClient(
                new OkHttpClient(), server.url("/v1/messages").toString(), "claude-sonnet-5", "test-api-key");

        ChatResponse response = client.chat(
                Collections.singletonList(ChatMessage.user(Collections.singletonList(ContentBlock.text("What day is it?")))),
                Collections.emptyList());

        assertFalse(response.hasToolUse());
        assertEquals("Today is Thursday.", response.textJoined());
    }

    @Test
    public void nonSuccessfulHttpResponseThrows() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"internal\"}"));

        AnthropicLlmClient client = new AnthropicLlmClient(
                new OkHttpClient(), server.url("/v1/messages").toString(), "claude-sonnet-5", "test-api-key");

        try {
            client.chat(Collections.singletonList(
                    ChatMessage.user(Collections.singletonList(ContentBlock.text("Hi")))), Collections.emptyList());
            fail("Expected LlmException on non-2xx response");
        } catch (LlmException expected) {
            // expected: MailAgent's catch(RuntimeException) turns this into a graceful fallback
        }
    }

    @Test
    public void toolResultMessageIsSerializedCorrectly() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"content\":[{\"type\":\"text\",\"text\":\"done\"}],\"stop_reason\":\"end_turn\"}"));

        AnthropicLlmClient client = new AnthropicLlmClient(
                new OkHttpClient(), server.url("/v1/messages").toString(), "claude-sonnet-5", "test-api-key");

        List<ChatMessage> messages = Arrays.asList(
                ChatMessage.user(Collections.singletonList(ContentBlock.text("What time is it?"))),
                ChatMessage.assistant(Collections.singletonList(
                        ContentBlock.toolUse("toolu_1", "current_datetime", com.mailagent.support.Json.MAPPER.createObjectNode()))),
                ChatMessage.user(Collections.singletonList(ContentBlock.toolResult("toolu_1", "2026-08-13T09:00:00Z"))));

        client.chat(messages, Collections.emptyList());

        RecordedRequest request = server.takeRequest();
        JsonNode body = com.mailagent.support.Json.MAPPER.readTree(request.getBody().readUtf8());
        JsonNode toolResultBlock = body.get("messages").get(2).get("content").get(0);
        assertEquals("tool_result", toolResultBlock.get("type").asText());
        assertEquals("toolu_1", toolResultBlock.get("tool_use_id").asText());
        assertEquals("2026-08-13T09:00:00Z", toolResultBlock.get("content").asText());
    }
}
