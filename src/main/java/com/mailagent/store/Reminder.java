package com.mailagent.store;

/**
 * Plain data holder for a stored reminder. Public no-arg constructor + getters/setters
 * so Jackson can (de)serialize it without extra annotations.
 */
public class Reminder {

    private String id;
    private String text;
    private String dueIso;

    public Reminder() {
    }

    public Reminder(String id, String text, String dueIso) {
        this.id = id;
        this.text = text;
        this.dueIso = dueIso;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDueIso() {
        return dueIso;
    }

    public void setDueIso(String dueIso) {
        this.dueIso = dueIso;
    }
}
