package com.mailagent.mail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MockMailChannelTest {

    @Test
    public void fetchUnreadReturnsConfiguredMessages() {
        Msg msg1 = new Msg("id-1", "alice@example.com", "Subject 1", "Body 1");
        Msg msg2 = new Msg("id-2", "bob@example.com", "Subject 2", "Body 2");
        MockMailChannel channel = new MockMailChannel(Arrays.asList(msg1, msg2));

        List<Msg> unread = channel.fetchUnread();

        assertEquals(Arrays.asList(msg1, msg2), unread);
    }

    @Test
    public void fetchUnreadIsRepeatableAcrossPolls() {
        Msg msg = new Msg("id-1", "alice@example.com", "Subject", "Body");
        MockMailChannel channel = new MockMailChannel(Collections.singletonList(msg));

        List<Msg> first = channel.fetchUnread();
        List<Msg> second = channel.fetchUnread();

        assertEquals(first, second);
    }

    @Test
    public void replyRecordsSentReplies() {
        Msg msg = new Msg("id-1", "alice@example.com", "Subject", "Body");
        MockMailChannel channel = new MockMailChannel(Collections.singletonList(msg));

        channel.reply(msg, "Here is your reply");

        assertEquals(1, channel.replies().size());
        assertEquals(msg, channel.replies().get(0).getMsg());
        assertEquals("Here is your reply", channel.replies().get(0).getBody());
    }

    @Test
    public void emptyChannelHasNoUnreadAndNoReplies() {
        MockMailChannel channel = new MockMailChannel(Collections.emptyList());

        assertTrue(channel.fetchUnread().isEmpty());
        assertTrue(channel.replies().isEmpty());
    }
}
