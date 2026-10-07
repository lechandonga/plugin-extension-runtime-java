package com.github.lechandonga.pluginextensionruntimejava.ext.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 扩展清单 extension.json 的内存模型。
 */
public record Manifest(
        String id,
        String name,
        Version version,
        String apiVersion,
        String className,
        Map<String, String> defaultConfig,
        Map<String, VersionRange> dependencies) {

    public Manifest {
        defaultConfig = defaultConfig == null ? Map.of() : Map.copyOf(defaultConfig);
        dependencies = dependencies == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(dependencies));
    }
}
