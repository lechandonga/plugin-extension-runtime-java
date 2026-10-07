package com.github.lechandonga.pluginextensionruntimejava.ext.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 按确定的优先级解析某个扩展独立命名空间下的配置。
 */
public class ConfigResolver {

    private final Path overrideFile;

    public ConfigResolver(Path overrideFile) {
        this.overrideFile = overrideFile;
    }

    public ConfigResolution resolve(String extensionId, Map<String, String> manifestDefaults) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();

        if (manifestDefaults != null) {
            for (Map.Entry<String, String> entry : manifestDefaults.entrySet()) {
                values.put(entry.getKey(), entry.getValue());
                sources.put(entry.getKey(), ConfigResolution.SOURCE_MANIFEST_DEFAULT);
            }
        }

        if (overrideFile != null && Files.isRegularFile(overrideFile)) {
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(overrideFile)) {
                properties.load(in);
            }
            String prefix = "ext." + extensionId + ".";
            for (String name : properties.stringPropertyNames()) {
                if (name.startsWith(prefix)) {
                    String key = name.substring(prefix.length());
                    values.put(key, properties.getProperty(name));
                    sources.put(key, ConfigResolution.overrideFileSource(overrideFile.toString()));
                }
            }
        }

        String sysPrefix = "ext." + extensionId + ".";
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (name.startsWith(sysPrefix)) {
                String key = name.substring(sysPrefix.length());
                values.put(key, String.valueOf(entry.getValue()));
                sources.put(key, ConfigResolution.SOURCE_SYSTEM_PROPERTY);
            }
        }

        return new ConfigResolution(values, sources);
    }
}
