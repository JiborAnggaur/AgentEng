package com.mailagent.agent;

import com.mailagent.audit.AuditLogger;
import com.mailagent.mail.MailChannel;
import com.mailagent.mail.MailException;
import com.mailagent.mail.Msg;
import com.mailagent.store.SeenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Drives one poll cycle: fetch unread mail, skip anything already seen, run
 * the tool loop for the rest, reply, then mark seen. A message is only ever
 * marked seen once we've actually sent a reply for it — if the LLM/tool loop
 * blows up we still send a graceful fallback and mark it seen (so a broken
 * model can't make the same email spam a fallback every cycle forever); if
 * sending the reply itself fails (COM error), we leave it unseen so the next
 * poll retries it.
 */
public class MailAgent {

    private static final Logger log = LoggerFactory.getLogger(MailAgent.class);

    private static final String FALLBACK_REPLY =
            "Извините, не удалось обработать ваш запрос из-за временной технической проблемы. Мы вернёмся к нему позже.";

    private final MailChannel mailChannel;
    private final ToolLoop toolLoop;
    private final SeenStore seenStore;
    private final AuditLogger auditLogger;
    private final String systemPrompt;

    public MailAgent(MailChannel mailChannel, ToolLoop toolLoop, SeenStore seenStore,
                      AuditLogger auditLogger, String systemPrompt) {
        this.mailChannel = mailChannel;
        this.toolLoop = toolLoop;
        this.seenStore = seenStore;
        this.auditLogger = auditLogger;
        this.systemPrompt = systemPrompt;
    }

    public void pollOnce() {
        for (Msg msg : mailChannel.fetchUnread()) {
            if (seenStore.isSeen(msg.getId())) {
                continue;
            }
            processOne(msg);
        }
    }

    private void processOne(Msg msg) {
        String replyBody;
        try {
            replyBody = toolLoop.run(systemPrompt, msg.getBody());
        } catch (RuntimeException e) {
            log.warn("agent_llm_failed reason={}", e.getMessage());
            replyBody = FALLBACK_REPLY;
        }

        try {
            mailChannel.reply(msg, replyBody);
        } catch (MailException e) {
            log.warn("agent_mail_reply_failed reason={}", e.getMessage());
            return;
        }

        markSeenAndAudit(msg);
    }

    private void markSeenAndAudit(Msg msg) {
        try {
            seenStore.markSeen(msg.getId());
        } catch (IOException e) {
            log.warn("agent_seen_store_failed reason={}", e.getMessage());
        }
        try {
            auditLogger.append("agent_mail_seen msgId=" + msg.getId());
        } catch (IOException e) {
            log.warn("agent_audit_failed reason={}", e.getMessage());
        }
    }
}
