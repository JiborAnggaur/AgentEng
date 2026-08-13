package com.mailagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mailagent.support.Json;

import java.time.Clock;

/**
 * Returns the current instant. The Clock is injected so tests can pin the
 * result instead of racing wall-clock time.
 */
public class CurrentDatetimeTool implements Tool {

    private final Clock clock;

    public CurrentDatetimeTool(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String name() {
        return "current_datetime";
    }

    @Override
    public String description() {
        return "Returns the current date and time as an ISO-8601 instant (UTC).";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = Json.MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    @Override
    public String execute(JsonNode args) {
        return clock.instant().toString();
    }
}
