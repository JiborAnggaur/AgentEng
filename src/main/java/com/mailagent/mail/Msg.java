package com.mailagent.mail;

import java.util.Objects;

/**
 * A single inbound email, reduced to the fields the agent needs. {@code id}
 * must be stable across restarts/re-fetches (Outlook EntryID) — it is the
 * dedup key SeenStore keys idempotency on.
 */
public final class Msg {

    private final String id;
    private final String from;
    private final String subject;
    private final String body;

    public Msg(String id, String from, String subject, String body) {
        this.id = id;
        this.from = from;
        this.subject = subject;
        this.body = body;
    }

    public String getId() {
        return id;
    }

    public String getFrom() {
        return from;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Msg)) return false;
        Msg other = (Msg) o;
        return Objects.equals(id, other.id)
                && Objects.equals(from, other.from)
                && Objects.equals(subject, other.subject)
                && Objects.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, from, subject, body);
    }
}
