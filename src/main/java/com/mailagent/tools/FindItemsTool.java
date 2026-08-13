package com.mailagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mailagent.store.Reminder;
import com.mailagent.store.ReminderStore;
import com.mailagent.support.Json;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Searches stored reminders by a case-insensitive substring match on their text.
 */
public class FindItemsTool implements Tool {

    private final ReminderStore store;

    public FindItemsTool(ReminderStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "find_items";
    }

    @Override
    public String description() {
        return "Finds stored reminders whose text contains the given query (case-insensitive).";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = Json.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("query").put("type", "string");
        schema.putArray("required").add("query");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws ToolExecutionException {
        String query = textOf(args, "query");
        String needle = query.toLowerCase(Locale.ROOT);

        List<Reminder> matches = store.list().stream()
                .filter(r -> r.getText().toLowerCase(Locale.ROOT).contains(needle))
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            return "No reminders found matching \"" + query + "\".";
        }

        return matches.stream()
                .map(r -> "- " + r.getText() + " (due " + r.getDueIso() + ")")
                .collect(Collectors.joining("\n", "Found " + matches.size() + " reminder(s):\n", ""));
    }

    private static String textOf(JsonNode args, String field) throws ToolExecutionException {
        JsonNode value = args.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().trim().isEmpty()) {
            throw new ToolExecutionException("Missing required argument: " + field);
        }
        return value.asText();
    }
}
