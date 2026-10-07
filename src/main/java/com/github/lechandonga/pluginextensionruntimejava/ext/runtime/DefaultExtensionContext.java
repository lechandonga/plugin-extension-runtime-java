package com.github.lechandonga.pluginextensionruntimejava.ext.runtime;

import com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension;
import com.github.lechandonga.pluginextensionruntimejava.ext.spi.ExtensionContext;

import java.util.Map;

/**
 * 注入给扩展的上下文实现。依赖映射在 start 阶段按拓扑序增量填充，
 * 扩展启动时其声明的依赖必然已经在映射中。
 */
final class DefaultExtensionContext implements ExtensionContext {

    private final String selfId;
    private final Map<String, String> configValues;
    private final Map<String, ActiveExtension> dependencies;

    DefaultExtensionContext(String selfId, Map<String, String> configValues,
                            Map<String, ActiveExtension> dependencies) {
        this.selfId = selfId;
        this.configValues = configValues;
        this.dependencies = dependencies;
    }

    @Override
    public String selfId() {
        return selfId;
    }

    @Override
    public String getConfig(String key) {
        return configValues.get(key);
    }

    @Override
    public String getConfig(String key, String defaultValue) {
        return configValues.getOrDefault(key, defaultValue);
    }

    @Override
    public Extension requireDependency(String dependencyId) {
        ActiveExtension ext = dependencies.get(dependencyId);
        if (ext == null) {
            throw new IllegalArgumentException("依赖扩展未就绪: " + dependencyId);
        }
        return ext.instance();
    }
}
