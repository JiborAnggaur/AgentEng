package com.mailagent.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks which mail ids (Outlook EntryID / Message-ID) have already been
 * replied to. Backed by a plain-text, append-only file so marking an id seen
 * is a single durable write and a fresh instance on the same file recovers
 * the full set after a process restart — the idempotency guarantee doesn't
 * depend on the process staying up.
 */
public class SeenStore {

    private final Path path;
    private final Set<String> seen;

    public SeenStore(Path path) throws IOException {
        this.path = path;
        this.seen = new HashSet<>(load(path));
    }

    public synchronized boolean isSeen(String id) {
        return seen.contains(id);
    }

    public synchronized void markSeen(String id) throws IOException {
        if (seen.contains(id)) {
            return;
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.write(path, Collections.singletonList(id), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        seen.add(id);
    }

    private static List<String> load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }
}
