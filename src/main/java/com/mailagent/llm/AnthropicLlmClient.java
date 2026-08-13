package com.mailagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mailagent.support.Json;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * {@link LlmClient} over the real Anthropic Messages API
 * (POST /v1/messages, x-api-key + anthropic-version headers). The system
 * prompt has no dedicated field here because the caller (ToolLoop) already
 * folds it into the first user message's content blocks — this class only
 * has to speak the wire format faithfully.
 */
public class AnthropicLlmClient implements LlmClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int DEFAULT_MAX_TOKENS = 1024;

    private final OkHttpClient httpClient;
    private final String endpoint;
    private final String model;
    private final String apiKey;

    public AnthropicLlmClient(OkHttpClient httpClient, String endpoint, String model, String apiKey) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools) {
        ObjectNode requestBody = Json.MAPPER.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("max_tokens", DEFAULT_MAX_TOKENS);
        requestBody.set("messages", messagesToJson(messages));
        if (!tools.isEmpty()) {
            requestBody.set("tools", toolsToJson(tools));
        }

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .post(RequestBody.create(JSON, requestBody.toString()))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String responseText = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new LlmException("Anthropic API returned HTTP " + response.code());
            }
            return parseResponse(responseText);
        } catch (IOException e) {
            throw new LlmException("Anthropic API call failed", e);
        }
    }

    private static ArrayNode messagesToJson(List<ChatMessage> messages) {
        ArrayNode array = Json.MAPPER.createArrayNode();
        for (ChatMessage message : messages) {
            ObjectNode node = array.addObject();
            node.put("role", message.getRole());
            ArrayNode content = node.putArray("content");
            for (ContentBlock block : message.getContent()) {
                content.add(contentBlockToJson(block));
            }
        }
        return array;
    }

    private static ObjectNode contentBlockToJson(ContentBlock block) {
        ObjectNode node = Json.MAPPER.createObjectNode();
        switch (block.getType()) {
            case "text":
                node.put("type", "text");
                node.put("text", block.getText());
                break;
            case "tool_use":
                node.put("type", "tool_use");
                node.put("id", block.getToolUseId());
                node.put("name", block.getToolName());
                node.set("input", block.getToolInput());
                break;
            case "tool_result":
                node.put("type", "tool_result");
                node.put("tool_use_id", block.getToolUseId());
                node.put("content", block.getToolResultContent());
                break;
            default:
                throw new LlmException("Unknown content block type: " + block.getType());
        }
        return node;
    }

    private static ArrayNode toolsToJson(List<ToolSpec> tools) {
        ArrayNode array = Json.MAPPER.createArrayNode();
        for (ToolSpec tool : tools) {
            ObjectNode node = array.addObject();
            node.put("name", tool.getName());
            node.put("description", tool.getDescription());
            node.set("input_schema", tool.getInputSchema());
        }
        return array;
    }

    private static ChatResponse parseResponse(String responseText) {
        JsonNode root;
        try {
            root = Json.MAPPER.readTree(responseText);
        } catch (IOException e) {
            throw new LlmException("Failed to parse Anthropic API response", e);
        }

        java.util.List<ContentBlock> blocks = new java.util.ArrayList<>();
        Iterator<JsonNode> elements = root.path("content").elements();
        while (elements.hasNext()) {
            blocks.add(contentBlockFromJson(elements.next()));
        }
        String stopReason = root.path("stop_reason").asText(null);
        return new ChatResponse(blocks, stopReason);
    }

    private static ContentBlock contentBlockFromJson(JsonNode node) {
        String type = node.path("type").asText("");
        switch (type) {
            case "text":
                return ContentBlock.text(node.path("text").asText(""));
            case "tool_use":
                return ContentBlock.toolUse(
                        node.path("id").asText(null),
                        node.path("name").asText(null),
                        node.path("input"));
            case "tool_result":
                return ContentBlock.toolResult(
                        node.path("tool_use_id").asText(null),
                        node.path("content").asText(null));
            default:
                throw new LlmException("Unknown content block type in Anthropic response: " + type);
        }
    }
}
