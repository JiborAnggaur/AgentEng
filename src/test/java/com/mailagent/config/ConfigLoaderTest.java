package com.mailagent.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class ConfigLoaderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String YAML =
            "llm:\n"
            + "  endpoint: https://api.anthropic.com/v1/messages\n"
            + "  model: claude-sonnet-5\n"
            + "  apiKeyEnv: ANTHROPIC_API_KEY\n"
            + "  timeoutMs: 30000\n"
            + "agent:\n"
            + "  maxSteps: 6\n"
            + "store:\n"
            + "  path: ./data\n"
            + "mail:\n"
            + "  pollSeconds: 30\n"
            + "  profile: Outlook\n"
            + "  folder: Inbox\n"
            + "audit:\n"
            + "  path: ./data/audit.log\n"
            + "  hmacKeyEnv: AUDIT_HMAC_KEY\n";

    @Test
    public void loadsAllFieldsFromYaml() throws IOException {
        File file = new File(tmp.getRoot(), "config.yaml");
        Files.write(file.toPath(), YAML.getBytes(StandardCharsets.UTF_8));

        AppConfig config = ConfigLoader.load(file.toPath());

        assertEquals("https://api.anthropic.com/v1/messages", config.getLlm().getEndpoint());
        assertEquals("claude-sonnet-5", config.getLlm().getModel());
        assertEquals("ANTHROPIC_API_KEY", config.getLlm().getApiKeyEnv());
        assertEquals(30000, config.getLlm().getTimeoutMs());

        assertEquals(6, config.getAgent().getMaxSteps());

        assertEquals("./data", config.getStore().getPath());

        assertEquals(30, config.getMail().getPollSeconds());
        assertEquals("Outlook", config.getMail().getProfile());
        assertEquals("Inbox", config.getMail().getFolder());

        assertEquals("./data/audit.log", config.getAudit().getPath());
        assertEquals("AUDIT_HMAC_KEY", config.getAudit().getHmacKeyEnv());
    }

    @Test(expected = IOException.class)
    public void missingFileRaisesIoException() throws IOException {
        ConfigLoader.load(new File(tmp.getRoot(), "does-not-exist.yaml").toPath());
    }
}
