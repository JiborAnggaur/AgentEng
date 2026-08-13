package com.mailagent.mail;

import java.util.List;

/**
 * Abstraction over the mailbox transport. {@link #fetchUnread()} is called
 * on every poll cycle and may legitimately return the same message more than
 * once (dedup is the caller's job via SeenStore) — the channel itself makes
 * no idempotency guarantee.
 */
public interface MailChannel {

    List<Msg> fetchUnread();

    void reply(Msg msg, String body);
}
