package com.github.lechandonga.pluginextensionruntimejava.extension;

import com.github.lechandonga.pluginextensionruntimejava.extension.config.ExtensionConfig;

/**
 * 扩展启动时注入的上下文，携带该扩展独立命名空间下的最终生效配置。
 */
public record ExtensionContext(String extensionId, String version, ExtensionConfig config) {

    public String getConfig(String key) {
        return config.get(key);
    }

    public String getConfig(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }
}
