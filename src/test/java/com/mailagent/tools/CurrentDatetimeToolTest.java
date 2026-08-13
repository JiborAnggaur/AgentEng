package com.mailagent.tools;

import com.mailagent.support.Json;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;

public class CurrentDatetimeToolTest {

    @Test
    public void returnsIsoInstantFromInjectedClock() throws ToolExecutionException {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-13T09:15:00Z"), ZoneOffset.UTC);
        CurrentDatetimeTool tool = new CurrentDatetimeTool(fixed);

        String result = tool.execute(Json.MAPPER.createObjectNode());

        assertEquals("2026-08-13T09:15:00Z", result);
    }

    @Test
    public void nameAndSchemaAreStableForToolRegistration() {
        CurrentDatetimeTool tool = new CurrentDatetimeTool(Clock.systemUTC());

        assertEquals("current_datetime", tool.name());
        assertEquals("object", tool.inputSchema().get("type").asText());
    }
}
