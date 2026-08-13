package com.mailagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches tool_use calls by name. Never lets a tool's failure — expected or
 * not — escape as an exception: the caller (the tool loop) always gets a
 * result string it can hand back to the model as a tool_result.
 */
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Tool> byName = new HashMap<>();
    private final List<Tool> tools;

    public ToolRegistry(List<Tool> tools) {
        this.tools = new ArrayList<>(tools);
        for (Tool tool : tools) {
            byName.put(tool.name(), tool);
        }
    }

    public List<Tool> tools() {
        return Collections.unmodifiableList(tools);
    }

    public String dispatch(String toolName, JsonNode args) {
        Tool tool = byName.get(toolName);
        if (tool == null) {
            log.warn("tool_dispatch_unknown tool={}", toolName);
            return "Error: unknown tool '" + toolName + "'";
        }
        try {
            return tool.execute(args);
        } catch (ToolExecutionException e) {
            log.warn("tool_dispatch_failed tool={} reason={}", toolName, e.getMessage());
            return "Error: " + e.getMessage();
        } catch (RuntimeException e) {
            log.warn("tool_dispatch_crashed tool={} reason={}", toolName, e.getMessage());
            return "Error: tool '" + toolName + "' failed unexpectedly";
        }
    }
}
