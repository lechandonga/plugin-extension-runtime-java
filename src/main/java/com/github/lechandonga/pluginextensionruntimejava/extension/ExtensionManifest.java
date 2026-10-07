package com.github.lechandonga.pluginextensionruntimejava.extension;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 扩展清单（extension-manifest.json）的映射模型。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtensionManifest(
        String id,
        String name,
        String version,
        /** 扩展编译所针对的框架 API 主版本号，须与框架当前版本一致。 */
        String apiVersion,
        /** 实现 {@link Extension} 的全限定类名。 */
        String implementation,
        List<DependencyDecl> dependencies,
        /** 清单内置默认配置，优先级最低。 */
        Map<String, String> config) {

    public record DependencyDecl(String id, String version) {
    }

    public List<DependencyDecl> safeDependencies() {
        return dependencies == null ? List.of() : dependencies;
    }

    public Map<String, String> safeConfig() {
        return config == null ? Map.of() : config;
    }
}
