package com.mailagent.support;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Single shared Jackson mapper instance used across YAML config, JSON stores,
 * the audit log and the Anthropic HTTP client, so tool-argument parsing and
 * schema building stay consistent everywhere.
 */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }
}
