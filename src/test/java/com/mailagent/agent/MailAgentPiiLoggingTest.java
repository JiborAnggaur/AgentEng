package com.mailagent.agent;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mailagent.audit.AuditLogger;
import com.mailagent.llm.ChatResponse;
import com.mailagent.llm.ContentBlock;
import com.mailagent.llm.MockLlmClient;
import com.mailagent.mail.MockMailChannel;
import com.mailagent.mail.Msg;
import com.mailagent.store.ReminderStore;
import com.mailagent.store.SeenStore;
import com.mailagent.tools.AddReminderTool;
import com.mailagent.tools.CurrentDatetimeTool;
import com.mailagent.tools.FindItemsTool;
import com.mailagent.tools.Tool;
import com.mailagent.tools.ToolRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Runs full MailAgent cycles (success path and LLM-failure fallback path)
 * with an email body containing PII, and asserts that no logged event —
 * success or failure — ever contains the body text or the PII substring.
 * This is a security-invariant check on the existing WARN-only, event-key
 * logging design (ToolRegistry/MailAgent never pass raw args/bodies to
 * SLF4J), not a driver for new production code.
 */
public class MailAgentPiiLoggingTest {

    private static final String SENSITIVE_PHONE = "+7-900-123-45-67";
    private static final String SENSITIVE_BODY =
            "Мой номер телефона " + SENSITIVE_PHONE + ", напомни позвонить Ивану";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;

    @Before
    public void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(appender);
    }

    @After
    public void detachAppender() {
        rootLogger.detachAppender(appender);
    }

    @Test
    public void successfulReplyNeverLogsEmailBodyOrPii() throws Exception {
        MockLlmClient llmClient = new MockLlmClient(Collections.singletonList(
                new ChatResponse(Collections.singletonList(ContentBlock.text("Хорошо, напомню.")), "end_turn")));

        runCycleAndAssertNoLeak(llmClient, "id-pii-1");
    }

    @Test
    public void llmFailureFallbackNeverLogsEmailBodyOrPii() throws Exception {
        MockLlmClient llmClient = new MockLlmClient(Collections.emptyList());

        runCycleAndAssertNoLeak(llmClient, "id-pii-2");
    }

    private void runCycleAndAssertNoLeak(MockLlmClient llmClient, String msgId) throws Exception {
        ReminderStore store = new ReminderStore(new File(tmp.getRoot(), "reminders-" + msgId + ".json").toPath());
        Clock clock = Clock.systemUTC();
        ToolRegistry toolRegistry = new ToolRegistry(Arrays.<Tool>asList(
                new CurrentDatetimeTool(clock), new AddReminderTool(store), new FindItemsTool(store)));
        ToolLoop toolLoop = new ToolLoop(llmClient, toolRegistry, 6);
        SeenStore seenStore = new SeenStore(new File(tmp.getRoot(), "seen-" + msgId + ".txt").toPath());
        AuditLogger auditLogger = new AuditLogger(new File(tmp.getRoot(), "audit-" + msgId + ".log").toPath(), null);

        Msg msg = new Msg(msgId, "user@example.com", "Личные данные", SENSITIVE_BODY);
        MockMailChannel mailChannel = new MockMailChannel(Collections.singletonList(msg));
        MailAgent agent = new MailAgent(mailChannel, toolLoop, seenStore, auditLogger, "You are Kolya.");

        agent.pollOnce();

        assertTrue("expected a reply to be sent", !mailChannel.replies().isEmpty());

        for (ILoggingEvent event : appender.list) {
            String formatted = event.getFormattedMessage();
            assertFalse("log event leaked email body: " + formatted, formatted.contains(SENSITIVE_BODY));
            assertFalse("log event leaked PII (phone number): " + formatted, formatted.contains(SENSITIVE_PHONE));
        }
    }
}
