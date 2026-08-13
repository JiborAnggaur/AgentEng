package com.mailagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mailagent.store.Reminder;
import com.mailagent.store.ReminderStore;
import com.mailagent.support.Json;

import java.io.IOException;

/**
 * Creates a reminder from LLM-supplied {@code text}/{@code dueIso} arguments.
 */
public class AddReminderTool implements Tool {

    private final ReminderStore store;

    public AddReminderTool(ReminderStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "add_reminder";
    }

    @Override
    public String description() {
        return "Adds a reminder with the given text and an ISO-8601 due date/time.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = Json.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("text").put("type", "string");
        properties.putObject("dueIso").put("type", "string");
        schema.putArray("required").add("text").add("dueIso");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws ToolExecutionException {
        String text = textOf(args, "text");
        String dueIso = textOf(args, "dueIso");

        Reminder reminder = addReminder(text, dueIso);
        return "Reminder added: \"" + reminder.getText() + "\" due " + reminder.getDueIso();
    }

    private Reminder addReminder(String text, String dueIso) throws ToolExecutionException {
        try {
            return store.add(text, dueIso);
        } catch (IOException e) {
            throw new ToolExecutionException("Failed to persist reminder", e);
        }
    }

    private static String textOf(JsonNode args, String field) throws ToolExecutionException {
        JsonNode value = args.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().trim().isEmpty()) {
            throw new ToolExecutionException("Missing required argument: " + field);
        }
        return value.asText();
    }
}
