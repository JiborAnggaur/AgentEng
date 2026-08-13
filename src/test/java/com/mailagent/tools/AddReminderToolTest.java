package com.mailagent.tools;

import com.mailagent.store.Reminder;
import com.mailagent.store.ReminderStore;
import com.mailagent.support.Json;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AddReminderToolTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void executeStoresReminderAndReturnsConfirmation() throws Exception {
        ReminderStore store = new ReminderStore(new File(tmp.getRoot(), "reminders.json").toPath());
        AddReminderTool tool = new AddReminderTool(store);

        String args = "{\"text\":\"Buy milk\",\"dueIso\":\"2026-08-14T10:00:00Z\"}";
        String result = tool.execute(Json.MAPPER.readTree(args));

        assertTrue(result.contains("Buy milk") || result.toLowerCase().contains("added"));

        List<Reminder> all = store.list();
        assertEquals(1, all.size());
        assertEquals("Buy milk", all.get(0).getText());
        assertEquals("2026-08-14T10:00:00Z", all.get(0).getDueIso());
    }

    @Test(expected = ToolExecutionException.class)
    public void executeRejectsMissingText() throws Exception {
        ReminderStore store = new ReminderStore(new File(tmp.getRoot(), "reminders.json").toPath());
        AddReminderTool tool = new AddReminderTool(store);

        tool.execute(Json.MAPPER.readTree("{\"dueIso\":\"2026-08-14T10:00:00Z\"}"));
    }

    @Test
    public void nameAndSchemaAreStableForToolRegistration() {
        AddReminderTool tool = new AddReminderTool(null);

        assertEquals("add_reminder", tool.name());
        assertEquals("object", tool.inputSchema().get("type").asText());
        assertTrue(tool.inputSchema().get("properties").has("text"));
        assertTrue(tool.inputSchema().get("properties").has("dueIso"));
    }
}
