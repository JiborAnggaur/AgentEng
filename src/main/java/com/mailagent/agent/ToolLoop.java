package com.mailagent.agent;

import com.mailagent.llm.ChatMessage;
import com.mailagent.llm.ChatResponse;
import com.mailagent.llm.ContentBlock;
import com.mailagent.llm.LlmClient;
import com.mailagent.llm.ToolSpec;
import com.mailagent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Drives the tool-calling conversation for a single unit of dialogue (one
 * email in, one final answer out). Bounded by maxSteps so a model that keeps
 * calling tools forever — or a broken/hallucinating one — can't hang the
 * poll loop; on that limit it returns a graceful fallback instead of the
 * (missing) final answer.
 */
public class ToolLoop {

    private static final Logger log = LoggerFactory.getLogger(ToolLoop.class);

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final int maxSteps;

    public ToolLoop(LlmClient llmClient, ToolRegistry toolRegistry, int maxSteps) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.maxSteps = maxSteps;
    }

    public String run(String systemPrompt, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(Arrays.asList(ContentBlock.text(systemPrompt), ContentBlock.text(userMessage))));

        List<ToolSpec> toolSpecs = toolRegistry.tools().stream().map(ToolSpec::from).collect(Collectors.toList());

        for (int step = 0; step < maxSteps; step++) {
            ChatResponse response = llmClient.chat(messages, toolSpecs);
            if (!response.hasToolUse()) {
                return response.textJoined();
            }

            messages.add(ChatMessage.assistant(response.getContent()));

            List<ContentBlock> toolResults = new ArrayList<>();
            for (ContentBlock toolUse : response.toolUseBlocks()) {
                String result = toolRegistry.dispatch(toolUse.getToolName(), toolUse.getToolInput());
                toolResults.add(ContentBlock.toolResult(toolUse.getToolUseId(), result));
            }
            messages.add(ChatMessage.user(toolResults));
        }

        log.warn("tool_loop_max_steps_exceeded maxSteps={}", maxSteps);
        return "Sorry, I could not finish this request within the allotted number of steps.";
    }
}
