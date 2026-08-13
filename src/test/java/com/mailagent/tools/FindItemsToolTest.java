package com.mailagent.tools;

import com.mailagent.store.ReminderStore;
import com.mailagent.support.Json;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FindItemsToolTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void executeFindsReminderByCaseInsensitiveSubstring() throws Exception {
        ReminderStore store = new ReminderStore(new File(tmp.getRoot(), "reminders.json").toPath());
        store.add("Buy milk", "2026-08-14T10:00:00Z");
        store.add("Call dentist", "2026-08-15T09:00:00Z");
        FindItemsTool tool = new FindItemsTool(store);

        String result = tool.execute(Json.MAPPER.readTree("{\"query\":\"milk\"}"));

        assertTrue(result.contains("Buy milk"));
        assertTrue(!result.contains("Call dentist"));
    }

    @Test
    public void executeReturnsNoMatchMessageWhenNothingFound() throws Exception {
        ReminderStore store = new ReminderStore(new File(tmp.getRoot(), "reminders.json").toPath());
        FindItemsTool tool = new FindItemsTool(store);

        String result = tool.execute(Json.MAPPER.readTree("{\"query\":\"nothing\"}"));

        assertTrue(result.toLowerCase().contains("no") || result.toLowerCase().contains("not found"));
    }

    @Test(expected = ToolExecutionException.class)
    public void executeRejectsMissingQuery() throws Exception {
        ReminderStore store = new ReminderStore(new File(tmp.getRoot(), "reminders.json").toPath());
        FindItemsTool tool = new FindItemsTool(store);

        tool.execute(Json.MAPPER.readTree("{}"));
    }

    @Test
    public void nameAndSchemaAreStableForToolRegistration() {
        FindItemsTool tool = new FindItemsTool(null);

        assertEquals("find_items", tool.name());
        assertEquals("object", tool.inputSchema().get("type").asText());
        assertTrue(tool.inputSchema().get("properties").has("query"));
    }
}
