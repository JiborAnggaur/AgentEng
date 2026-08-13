package com.mailagent.store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SeenStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void unseenIdIsNotSeenUntilMarked() throws Exception {
        SeenStore store = new SeenStore(new File(tmp.getRoot(), "seen.txt").toPath());

        assertFalse(store.isSeen("outlook-entry-id-1"));
        store.markSeen("outlook-entry-id-1");
        assertTrue(store.isSeen("outlook-entry-id-1"));
    }

    @Test
    public void seenStateSurvivesNewInstanceOnSameFile() throws Exception {
        File file = new File(tmp.getRoot(), "seen.txt");
        SeenStore first = new SeenStore(file.toPath());
        first.markSeen("outlook-entry-id-1");

        SeenStore second = new SeenStore(file.toPath());

        assertTrue(second.isSeen("outlook-entry-id-1"));
        assertFalse(second.isSeen("outlook-entry-id-2"));
    }

    @Test
    public void markingTheSameIdTwiceIsSafe() throws Exception {
        SeenStore store = new SeenStore(new File(tmp.getRoot(), "seen.txt").toPath());

        store.markSeen("outlook-entry-id-1");
        store.markSeen("outlook-entry-id-1");

        assertTrue(store.isSeen("outlook-entry-id-1"));
    }
}
