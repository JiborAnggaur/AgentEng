package com.mailagent.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mailagent.support.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * JSON-file-backed list of reminders. Rewrites the whole file on every add —
 * fine at the volumes a single mailbox produces, and keeps the on-disk format
 * trivially inspectable.
 */
public class ReminderStore {

    private final Path path;
    private final List<Reminder> reminders;

    public ReminderStore(Path path) throws IOException {
        this.path = path;
        this.reminders = new ArrayList<>(load(path));
    }

    public synchronized Reminder add(String text, String dueIso) throws IOException {
        Reminder reminder = new Reminder(UUID.randomUUID().toString(), text, dueIso);
        reminders.add(reminder);
        save();
        return reminder;
    }

    public synchronized List<Reminder> list() {
        return Collections.unmodifiableList(new ArrayList<>(reminders));
    }

    private void save() throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Json.MAPPER.writeValue(path.toFile(), reminders);
    }

    private static List<Reminder> load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }
        return Json.MAPPER.readValue(path.toFile(), new TypeReference<List<Reminder>>() {
        });
    }
}
