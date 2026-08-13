package com.mailagent.store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReminderStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void addedReminderIsReturnedByList() throws Exception {
        File file = new File(tmp.getRoot(), "reminders.json");
        ReminderStore store = new ReminderStore(file.toPath());

        Reminder created = store.add("Buy milk", "2026-08-14T10:00:00Z");

        assertEquals("Buy milk", created.getText());
        assertEquals("2026-08-14T10:00:00Z", created.getDueIso());
        assertTrue(created.getId() != null && !created.getId().isEmpty());

        List<Reminder> all = store.list();
        assertEquals(1, all.size());
        assertEquals(created.getId(), all.get(0).getId());
    }

    @Test
    public void reminderSurvivesNewStoreInstanceOnSameFile() throws Exception {
        File file = new File(tmp.getRoot(), "reminders.json");
        ReminderStore first = new ReminderStore(file.toPath());
        first.add("Call dentist", "2026-08-15T09:00:00Z");

        ReminderStore second = new ReminderStore(file.toPath());
        List<Reminder> all = second.list();

        assertEquals(1, all.size());
        assertEquals("Call dentist", all.get(0).getText());
    }
}
