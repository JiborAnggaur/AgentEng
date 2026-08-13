package com.mailagent.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory {@link MailChannel} for tests: fetchUnread() returns a fixed,
 * repeatable snapshot (no read/unread state is mutated, mirroring how
 * MailAgent relies on SeenStore rather than the channel for dedup) and
 * reply() records what was sent so tests can assert on it.
 */
public class MockMailChannel implements MailChannel {

    private final List<Msg> unread;
    private final List<Reply> replies = new ArrayList<>();

    public MockMailChannel(List<Msg> unread) {
        this.unread = new ArrayList<>(unread);
    }

    @Override
    public List<Msg> fetchUnread() {
        return Collections.unmodifiableList(new ArrayList<>(unread));
    }

    @Override
    public void reply(Msg msg, String body) {
        replies.add(new Reply(msg, body));
    }

    public List<Reply> replies() {
        return Collections.unmodifiableList(replies);
    }

    public static final class Reply {
        private final Msg msg;
        private final String body;

        Reply(Msg msg, String body) {
            this.msg = msg;
            this.body = body;
        }

        public Msg getMsg() {
            return msg;
        }

        public String getBody() {
            return body;
        }
    }
}
