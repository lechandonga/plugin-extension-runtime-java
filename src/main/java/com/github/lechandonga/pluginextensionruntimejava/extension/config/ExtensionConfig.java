package com.github.lechandonga.pluginextensionruntimejava.extension.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个扩展独立命名空间下的最终生效配置（不可变）。
 */
public final class ExtensionConfig {

    private final String namespace;
    private final Map<String, String> values;

    ExtensionConfig(String namespace, Map<String, String> values) {
        this.namespace = namespace;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public String namespace() {
        return namespace;
    }

    public String get(String key) {
        return values.get(key);
    }

    public String getOrDefault(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public Map<String, String> asMap() {
        return values;
    }
}
