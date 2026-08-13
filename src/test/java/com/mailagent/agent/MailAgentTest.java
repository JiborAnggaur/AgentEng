package com.mailagent.agent;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mailagent.audit.AuditLogger;
import com.mailagent.llm.ChatResponse;
import com.mailagent.llm.ContentBlock;
import com.mailagent.llm.MockLlmClient;
import com.mailagent.mail.MailChannel;
import com.mailagent.mail.MailException;
import com.mailagent.mail.MockMailChannel;
import com.mailagent.mail.Msg;
import com.mailagent.store.ReminderStore;
import com.mailagent.store.SeenStore;
import com.mailagent.support.Json;
import com.mailagent.tools.AddReminderTool;
import com.mailagent.tools.CurrentDatetimeTool;
import com.mailagent.tools.FindItemsTool;
import com.mailagent.tools.Tool;
import com.mailagent.tools.ToolRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the four golden scenarios from the assignment (§10: reminder, list,
 * date, junk mail) plus the two core robustness requirements — LLM-failure
 * fallback and idempotency across poll cycles — all against MockMailChannel
 * and MockLlmClient with the real tool implementations wired in.
 */
public class MailAgentTest {

    private static final String SYSTEM_PROMPT = "You are Kolya, an email assistant.";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void reminderEmailTriggersAddReminderAndConfirmationReply() throws Exception {
        ReminderStore store = reminderStore();
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);

        ObjectNode toolInput = Json.MAPPER.createObjectNode();
        toolInput.put("text", "позвонить Ивану");
        toolInput.put("dueIso", "2026-08-14T10:00:00Z");

        MockLlmClient llmClient = new MockLlmClient(Arrays.asList(
                new ChatResponse(Collections.singletonList(
                        ContentBlock.toolUse("call-1", "add_reminder", toolInput)), "tool_use"),
                new ChatResponse(Collections.singletonList(
                        ContentBlock.text("Напомню: позвонить Ивану 14.08 в 10:00.")), "end_turn")));

        ToolLoop toolLoop = new ToolLoop(llmClient, toolRegistry(store, clock), 6);
        Msg msg = new Msg("id-1", "user@example.com", "Напоминание", "Напомни завтра в 10 позвонить Ивану");
        MockMailChannel mailChannel = new MockMailChannel(Collections.singletonList(msg));
        MailAgent agent = new MailAgent(mailChannel, toolLoop, seenStore(), auditLogger(), SYSTEM_PROMPT);

        agent.pollOnce();

        assertEquals(1, mailChannel.replies().size());
        assertEquals("Напомню: позвонить Ивану 14.08 в 10:00.", mailChannel.replies().get(0).getBody());
        assertEquals(1, store.list().size());
        assertEquals("позвонить Ивану", store.list().get(0).getText());
    }

    @Test
    public void listEmailTriggersFindItemsAndListReply() throws Exception {
        ReminderStore store = reminderStore();
        store.add("позвонить Ивану", "2026-08-14T10:00:00Z");
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);

        ObjectNode toolInput = Json.MAPPER.createObjectNode();
        toolInput.put("query", "Иван");

        MockLlmClient llmClient = new MockLlmClient(Arrays.asList(
                new ChatResponse(Collections.singletonList(
                        ContentBlock.toolUse("call-1", "find_items", toolInput)), "tool_use"),
                new ChatResponse(Collections.singletonList(
                        ContentBlock.text("У вас запланировано: позвонить Ивану.")), "end_turn")));

        ToolLoop toolLoop = new ToolLoop(llmClient, toolRegistry(store, clock), 6);
        Msg msg = new Msg("id-2", "user@example.com", "Планы", "Что у меня запланировано?");
        MockMailChannel mailChannel = new MockMailChannel(Collections.singletonList(msg));
        MailAgent agent = new MailAgent(mailChannel, toolLoop, seenStore(), auditLogger(), SYSTEM_PROMPT);

        agent.pollOnce();

        assertEquals(1, mailChannel.replies().size());
        assertEquals("У вас запланировано: позвонить Ивану.", mailChannel.replies().get(0).getBody());
    }

    @Test
    public void dateEmailTriggersCurrentDatetimeReply() throws Exception {
        ReminderStore store = reminderStore();
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);

        MockLlmClient llmClient = new MockLlmClient(Arrays.asList(
                new ChatResponse(Collections.singletonList(
                        ContentBlock.toolUse("call-1", "current_datetime", Json.MAPPER.createObjectNode())), "tool_use"),
                new ChatResponse(Collections.singletonList(
                        ContentBlock.text("Сегодня 13 августа 2026 года.")), "end_turn")));

        ToolLoop toolLoop = new ToolLoop(llmClient, toolRegistry(store, clock), 6);
        Msg msg = new Msg("id-3", "user@example.com", "Дата", "Какое сегодня число?");
        MockMailChannel mailChannel = new MockMailChannel(Collections.singletonList(msg));
        MailAgent agent = new MailAgent(mailChannel, toolLoop, seenStore(), auditLogger(), SYSTEM_PROMPT);

        agent.pollOnce();

        assertEquals(1, mailChannel.replies().size());
        assertEquals("Сегодня 13 августа 2026 года.", mailChannel.replies().get(0).getBody());
    }

    @Test
    public void junkEmailGetsGracefulReplyWithoutToolCalls() throws Exception {
        ReminderStore store = reminderStore();
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);

        MockLlmClient llmClient = new MockLlmClient(Collections.singletonList(
                new ChatResponse(Collections.singletonList(
                        ContentBlock.text("Извините, не понял ваш запрос.")), "end_turn")));

        ToolLoop toolLoop = new ToolLoop(llmClient, toolRegistry(store, clock), 6);
        Msg msg = new Msg("id-4", "spam@example.com", "", "");
        MockMailChannel mailChannel = new MockMailChannel(Collections.singletonList(msg));
        MailAgent agent = new MailAgent(mailChannel, toolLoop, seenStore(), auditLogger(), SYSTEM_PROMPT);

        agent.pollOnce();

        assertEquals(1, mailChannel.replies().size());
        assertEquals("Извините, не понял ваш запрос.", mailChannel.replies().get(0).getBody());
        assertTrue(store.list().isEmpty());
        assertEquals(1, llmClient.requests().size());
    }

    @Test
    public void llmFailureFallsBackToGracefulReplyAndStillMarksSeen() throws Exception {
        ReminderStore store = reminderStore();
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);

        MockLlmClient llmClient = new MockLlmClient(Collections.emptyList());

        ToolLoop toolLoop = new ToolLoop(llmClient, toolRegistry(store, clock), 6);
        Msg msg = new Msg("id-5", "user@example.com", "Напоминание", "Напомни позвонить");
        MockMailChannel mailChannel = new MockMailChannel(Collections.singletonList(msg));
        SeenStore seenStore = seenStore();
        MailAgent agent = new MailAgent(mailChannel, toolLoop, seenStore, auditLogger(), SYSTEM_PROMPT);

        agent.pollOnce();

        assertEquals(1, mailChannel.replies().size());
        assertFalse(mailChannel.replies().get(0).getBody().isEmpty());
        assertTrue(seenStore.isSeen("id-5"));
    }

    @Test
    public void sameMessageIsNotRepliedToTwiceAcrossPolls() throws Exception {
        ReminderStore store = reminderStore();
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);

        MockLlmClient llmClient = new MockLlmClient(Collections.singletonList(
                new ChatResponse(Collections.singletonList(ContentBlock.text("Ok.")), "end_turn")));

        ToolLoop toolLoop = new ToolLoop(llmClient, toolRegistry(store, clock), 6);
        Msg msg = new Msg("id-6", "user@example.com", "Subject", "Body");
        MockMailChannel mailChannel = new MockMailChannel(Collections.singletonList(msg));
        MailAgent agent = new MailAgent(mailChannel, toolLoop, seenStore(), auditLogger(), SYSTEM_PROMPT);

        agent.pollOnce();
        agent.pollOnce();

        assertEquals(1, mailChannel.replies().size());
    }

    @Test
    public void mailReplyFailureLeavesMessageUnseenForRetry() throws Exception {
        ReminderStore store = reminderStore();
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);

        MockLlmClient llmClient = new MockLlmClient(Collections.singletonList(
                new ChatResponse(Collections.singletonList(ContentBlock.text("Ok.")), "end_turn")));

        ToolLoop toolLoop = new ToolLoop(llmClient, toolRegistry(store, clock), 6);
        Msg msg = new Msg("id-7", "user@example.com", "Subject", "Body");
        MailChannel failingChannel = new FailingReplyMailChannel(Collections.singletonList(msg));
        SeenStore seenStore = seenStore();
        MailAgent agent = new MailAgent(failingChannel, toolLoop, seenStore, auditLogger(), SYSTEM_PROMPT);

        agent.pollOnce();

        assertFalse(seenStore.isSeen("id-7"));
    }

    private ToolRegistry toolRegistry(ReminderStore store, Clock clock) {
        return new ToolRegistry(Arrays.<Tool>asList(
                new CurrentDatetimeTool(clock),
                new AddReminderTool(store),
                new FindItemsTool(store)));
    }

    private SeenStore seenStore() throws Exception {
        return new SeenStore(new File(tmp.getRoot(), "seen.txt").toPath());
    }

    private AuditLogger auditLogger() throws Exception {
        return new AuditLogger(new File(tmp.getRoot(), "audit.log").toPath(), null);
    }

    private ReminderStore reminderStore() throws Exception {
        return new ReminderStore(new File(tmp.getRoot(), "reminders.json").toPath());
    }

    private static final class FailingReplyMailChannel implements MailChannel {
        private final List<Msg> unread;

        FailingReplyMailChannel(List<Msg> unread) {
            this.unread = unread;
        }

        @Override
        public List<Msg> fetchUnread() {
            return unread;
        }

        @Override
        public void reply(Msg msg, String body) {
            throw new MailException("simulated COM failure");
        }
    }
}
