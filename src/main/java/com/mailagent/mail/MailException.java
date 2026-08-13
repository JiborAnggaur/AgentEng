package com.mailagent.mail;

/**
 * Unchecked wrapper for mail-transport failures (COM errors, disconnected
 * mailbox, etc.) so callers can catch a single type and fall back gracefully
 * instead of the agent crashing on a transient Outlook/COM hiccup.
 */
public class MailException extends RuntimeException {

    public MailException(String message) {
        super(message);
    }

    public MailException(String message, Throwable cause) {
        super(message, cause);
    }
}
