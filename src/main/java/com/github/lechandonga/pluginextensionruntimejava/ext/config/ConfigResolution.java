package com.github.lechandonga.pluginextensionruntimejava.ext.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个扩展配置的解析结果：最终生效值 + 每个 key 的来源说明（可解释优先级）。
 * 优先级从高到低：
 * <ol>
 *   <li>系统属性（-Dext.&lt;id&gt;.&lt;key&gt;=...），来源 SYSTEM_PROPERTY</li>
 *   <li>外部覆盖文件（override.properties），来源 OVERRIDE_FILE:&lt;path&gt;</li>
 *   <li>清单默认值（extension.json 的 configDefaults），来源 MANIFEST_DEFAULT</li>
 * </ol>
 */
public record ConfigResolution(Map<String, String> values, Map<String, String> sources) {

    public static final String SOURCE_SYSTEM_PROPERTY = "SYSTEM_PROPERTY";
    public static final String SOURCE_MANIFEST_DEFAULT = "MANIFEST_DEFAULT";

    public ConfigResolution {
        values = Map.copyOf(values);
        sources = Map.copyOf(new LinkedHashMap<>(sources));
    }

    public static String overrideFileSource(String path) {
        return "OVERRIDE_FILE:" + path;
    }
}
