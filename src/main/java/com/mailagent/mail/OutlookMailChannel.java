package com.mailagent.mail;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComThread;
import com.jacob.com.Dispatch;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/**
 * MailChannel backed by a running Outlook desktop client via JACOB COM
 * automation. Requires Windows + Outlook + the native jacob DLL on PATH.
 * Excluded from the test classpath (see pom.xml surefire config) — JACOB's
 * static initializer calls System.exit when the COM bridge can't load, which
 * would kill the JVM on a machine without Outlook.
 */
public class OutlookMailChannel implements MailChannel, Closeable {

    private static final int OL_FOLDER_INBOX = 6;
    private static final String UNREAD_FILTER = "[Unread]=true";

    private final Dispatch namespace;
    private final Dispatch folder;

    public OutlookMailChannel(String folderName) {
        try {
            ComThread.InitSTA();
            ActiveXComponent outlookApp = new ActiveXComponent("Outlook.Application");
            this.namespace = Dispatch.call(outlookApp, "GetNamespace", "MAPI").toDispatch();
            this.folder = resolveFolder(namespace, folderName == null || folderName.isEmpty() ? "Inbox" : folderName);
        } catch (RuntimeException e) {
            throw new MailException("Failed to initialize Outlook COM channel", e);
        }
    }

    private static Dispatch resolveFolder(Dispatch namespace, String folderName) {
        Dispatch inbox = Dispatch.call(namespace, "GetDefaultFolder", OL_FOLDER_INBOX).toDispatch();
        if ("Inbox".equalsIgnoreCase(folderName)) {
            return inbox;
        }
        Dispatch subFolders = Dispatch.get(inbox, "Folders").toDispatch();
        return Dispatch.call(subFolders, "Item", folderName).toDispatch();
    }

    @Override
    public List<Msg> fetchUnread() {
        try {
            List<Msg> result = new ArrayList<>();
            Dispatch items = Dispatch.get(folder, "Items").toDispatch();
            Dispatch unread = Dispatch.call(items, "Restrict", UNREAD_FILTER).toDispatch();
            int count = Dispatch.get(unread, "Count").getInt();
            for (int i = 1; i <= count; i++) {
                Dispatch item = Dispatch.call(unread, "Item", i).toDispatch();
                result.add(toMsg(item));
            }
            return result;
        } catch (RuntimeException e) {
            throw new MailException("Failed to fetch unread mail from Outlook", e);
        }
    }

    @Override
    public void reply(Msg msg, String body) {
        try {
            Dispatch item = Dispatch.call(namespace, "GetItemFromID", msg.getId()).toDispatch();
            Dispatch replyItem = Dispatch.call(item, "Reply").toDispatch();
            String quotedBody = Dispatch.get(replyItem, "Body").getString();
            Dispatch.put(replyItem, "Body", body + "\n\n" + quotedBody);
            Dispatch.call(replyItem, "Send");
        } catch (RuntimeException e) {
            throw new MailException("Failed to send Outlook reply", e);
        }
    }

    @Override
    public void close() {
        ComThread.Release();
    }

    private static Msg toMsg(Dispatch item) {
        String id = Dispatch.get(item, "EntryID").getString();
        String from = Dispatch.get(item, "SenderEmailAddress").getString();
        String subject = Dispatch.get(item, "Subject").getString();
        String body = Dispatch.get(item, "Body").getString();
        return new Msg(id, from, subject, body);
    }
}
