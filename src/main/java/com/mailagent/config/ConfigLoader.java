package com.mailagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private ConfigLoader() {
    }

    public static AppConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Config file not found: " + path);
        }
        return YAML_MAPPER.readValue(path.toFile(), AppConfig.class);
    }
}
