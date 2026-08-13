package com.mailagent.llm;

import java.util.List;

/**
 * A single turn with an LLM: send the conversation so far plus the available
 * tools, get back the assistant's next content blocks and stop reason.
 */
public interface LlmClient {

    ChatResponse chat(List<ChatMessage> messages, List<ToolSpec> tools);
}
