package com.mailagent.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mailagent.support.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

/**
 * Append-only, tamper-evident audit trail. Each line is a JSON record whose
 * hash covers the previous record's hash plus this record's payload — edit
 * any past line and every hash from that point on fails to recompute, which
 * is what {@link #verifyChain} checks. Uses HMAC-SHA256 when a key is
 * configured (so an attacker without the key can't forge a valid chain
 * after tampering); falls back to plain SHA-256 chaining (still tamper-evident,
 * just not attacker-proof without the key) with a one-time warning if none is set.
 */
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private static final String GENESIS_HASH = String.join("", Collections.nCopies(64, "0"));

    private final Path path;
    private final String hmacKey;
    private String lastHash;

    public AuditLogger(Path path, String hmacKey) throws IOException {
        this.path = path;
        this.hmacKey = hmacKey;
        if (hmacKey == null) {
            log.warn("audit_hmac_key_missing — chaining with unauthenticated SHA-256");
        }
        this.lastHash = readLastHash(path);
    }

    public synchronized void append(String payload) throws IOException {
        String prevHash = lastHash;
        String hash = computeHash(hmacKey, prevHash, payload);

        ObjectNode record = Json.MAPPER.createObjectNode();
        record.put("payload", payload);
        record.put("prevHash", prevHash);
        record.put("hash", hash);
        String line = Json.MAPPER.writeValueAsString(record);

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.write(path, Collections.singletonList(line), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        lastHash = hash;
    }

    public static boolean verifyChain(Path path, String hmacKey) throws IOException {
        if (!Files.exists(path)) {
            return true;
        }
        String expectedPrev = GENESIS_HASH;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            JsonNode record = Json.MAPPER.readTree(line);
            String payload = record.get("payload").asText();
            String prevHash = record.get("prevHash").asText();
            String hash = record.get("hash").asText();

            if (!expectedPrev.equals(prevHash)) {
                return false;
            }
            if (!computeHash(hmacKey, prevHash, payload).equals(hash)) {
                return false;
            }
            expectedPrev = hash;
        }
        return true;
    }

    private static String readLastHash(Path path) throws IOException {
        if (!Files.exists(path)) {
            return GENESIS_HASH;
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return GENESIS_HASH;
        }
        return Json.MAPPER.readTree(lines.get(lines.size() - 1)).get("hash").asText();
    }

    private static String computeHash(String hmacKey, String prevHash, String payload) {
        byte[] data = (prevHash + payload).getBytes(StandardCharsets.UTF_8);
        try {
            if (hmacKey != null) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return toHex(mac.doFinal(data));
            }
            return toHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute audit hash", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
