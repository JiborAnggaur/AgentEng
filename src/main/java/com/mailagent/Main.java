package com.mailagent;

import com.mailagent.agent.MailAgent;
import com.mailagent.agent.ToolLoop;
import com.mailagent.audit.AuditLogger;
import com.mailagent.config.AppConfig;
import com.mailagent.config.ConfigLoader;
import com.mailagent.llm.AnthropicLlmClient;
import com.mailagent.llm.LlmClient;
import com.mailagent.mail.MailChannel;
import com.mailagent.mail.OutlookMailChannel;
import com.mailagent.store.ReminderStore;
import com.mailagent.store.SeenStore;
import com.mailagent.tools.AddReminderTool;
import com.mailagent.tools.CurrentDatetimeTool;
import com.mailagent.tools.FindItemsTool;
import com.mailagent.tools.Tool;
import com.mailagent.tools.ToolRegistry;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bootstraps the agent, wires the real (Outlook/Anthropic) implementations,
 * and drives the poll loop until a shutdown signal (SIGINT/SIGTERM) arrives.
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String SYSTEM_PROMPT =
            "Ты — Коля, почтовый ассистент. Отвечай кратко и по-русски, "
                    + "используй инструменты, когда это уместно, и не выдумывай факты.";

    private Main() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            log.error("main_startup_failed", e);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Path configPath = Paths.get(args.length > 0 ? args[0] : "config.yaml");
        AppConfig config = ConfigLoader.load(configPath);

        Path storeDir = Paths.get(config.getStore().getPath());
        ReminderStore reminderStore = new ReminderStore(storeDir.resolve("reminders.json"));
        SeenStore seenStore = new SeenStore(storeDir.resolve("seen.txt"));
        AuditLogger auditLogger = new AuditLogger(
                Paths.get(config.getAudit().getPath()), envOrNull(config.getAudit().getHmacKeyEnv()));

        LlmClient llmClient = buildLlmClient(config);
        ToolRegistry toolRegistry = new ToolRegistry(Arrays.<Tool>asList(
                new CurrentDatetimeTool(Clock.systemUTC()),
                new AddReminderTool(reminderStore),
                new FindItemsTool(reminderStore)));
        ToolLoop toolLoop = new ToolLoop(llmClient, toolRegistry, config.getAgent().getMaxSteps());

        MailChannel mailChannel = new OutlookMailChannel(config.getMail().getFolder());
        MailAgent agent = new MailAgent(mailChannel, toolLoop, seenStore, auditLogger, SYSTEM_PROMPT);

        AtomicBoolean running = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("main_shutdown_signal_received");
            running.set(false);
        }));

        try {
            runPollLoop(agent, config.getMail().getPollSeconds(), running);
        } finally {
            if (mailChannel instanceof Closeable) {
                ((Closeable) mailChannel).close();
            }
        }
    }

    private static void runPollLoop(MailAgent agent, int pollSeconds, AtomicBoolean running) {
        while (running.get()) {
            try {
                agent.pollOnce();
            } catch (RuntimeException e) {
                log.warn("main_poll_cycle_failed reason={}", e.getMessage());
            }
            sleepInterruptibly(pollSeconds, running);
        }
        log.info("main_shutdown_complete");
    }

    private static void sleepInterruptibly(int pollSeconds, AtomicBoolean running) {
        try {
            for (int i = 0; i < pollSeconds && running.get(); i++) {
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    private static LlmClient buildLlmClient(AppConfig config) {
        String apiKey = envOrNull(config.getLlm().getApiKeyEnv());
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Missing LLM API key in env var " + config.getLlm().getApiKeyEnv());
        }
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getLlm().getTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getLlm().getTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
        return new AnthropicLlmClient(httpClient, config.getLlm().getEndpoint(), config.getLlm().getModel(), apiKey);
    }

    private static String envOrNull(String envVarName) {
        return envVarName == null ? null : System.getenv(envVarName);
    }
}
