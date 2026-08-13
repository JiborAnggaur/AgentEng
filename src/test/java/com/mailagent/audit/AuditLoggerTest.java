package com.mailagent.audit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuditLoggerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void chainOfUntamperedEntriesVerifies() throws Exception {
        Path path = new File(tmp.getRoot(), "audit.log").toPath();
        AuditLogger logger = new AuditLogger(path, "test-hmac-secret");

        logger.append("agent_mail_seen msgId=abc123");
        logger.append("agent_tool_call tool=current_datetime");

        assertTrue(AuditLogger.verifyChain(path, "test-hmac-secret"));
    }

    @Test
    public void tamperedPayloadBreaksChainVerification() throws Exception {
        Path path = new File(tmp.getRoot(), "audit.log").toPath();
        AuditLogger logger = new AuditLogger(path, "test-hmac-secret");

        logger.append("agent_mail_seen msgId=abc123");
        logger.append("agent_tool_call tool=current_datetime");

        List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
        String tampered = lines.get(0).replace("abc123", "HACKED");
        lines.set(0, tampered);
        Files.write(path, lines, StandardCharsets.UTF_8);

        assertFalse(AuditLogger.verifyChain(path, "test-hmac-secret"));
    }

    @Test
    public void chainWithoutHmacKeyStillDetectsTampering() throws Exception {
        Path path = new File(tmp.getRoot(), "audit.log").toPath();
        AuditLogger logger = new AuditLogger(path, null);

        logger.append("agent_mail_seen msgId=abc123");

        assertTrue(AuditLogger.verifyChain(path, null));

        List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
        lines.set(0, lines.get(0).replace("abc123", "HACKED"));
        Files.write(path, lines, StandardCharsets.UTF_8);

        assertFalse(AuditLogger.verifyChain(path, null));
    }
}
