package com.github.lechandonga.pluginextensionruntimejava.ext.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.Manifest;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.RejectReason;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.Version;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.VersionRange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析扩展目录下的 extension.json，任何字段缺失或格式错误均抛出 ManifestParseException。
 */
public class ManifestParser {

    private static final String MANIFEST_FILE = "extension.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Path manifestPath(Path extensionDir) {
        return extensionDir.resolve(MANIFEST_FILE);
    }

    public Manifest parse(Path extensionDir) throws ManifestParseException {
        Path file = manifestPath(extensionDir);
        if (!Files.isRegularFile(file)) {
            throw new ManifestParseException("缺少清单文件 extension.json: " + extensionDir);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(Files.readAllBytes(file));
        } catch (IOException ex) {
            throw new ManifestParseException("清单 JSON 无法解析: " + file + " (" + ex.getMessage() + ")");
        }
        if (root == null || !root.isObject()) {
            throw new ManifestParseException("清单根节点必须是 JSON 对象: " + file);
        }
        String id = requiredText(root, "id", file);
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new ManifestParseException("扩展 id 只能包含字母、数字、点、下划线、连字符且不以符号开头: " + id);
        }
        String name = textOr(root, "name", id);
        Version version;
        try {
            version = Version.parse(requiredText(root, "version", file));
        } catch (IllegalArgumentException ex) {
            throw new ManifestParseException("version 字段非法: " + ex.getMessage());
        }
        String apiVersion = requiredText(root, "apiVersion", file);
        String className = requiredText(root, "className", file);

        Map<String, String> defaults = new LinkedHashMap<>();
        JsonNode configNode = root.get("configDefaults");
        if (configNode != null) {
            if (!configNode.isObject()) {
                throw new ManifestParseException("configDefaults 必须是 JSON 对象: " + file);
            }
            Iterator<Map.Entry<String, JsonNode>> fields = configNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (!entry.getValue().isValueNode()) {
                    throw new ManifestParseException("configDefaults." + entry.getKey() + " 必须是标量值: " + file);
                }
                defaults.put(entry.getKey(), entry.getValue().asText());
            }
        }

        Map<String, VersionRange> dependencies = new LinkedHashMap<>();
        JsonNode depsNode = root.get("dependencies");
        if (depsNode != null) {
            if (!depsNode.isObject()) {
                throw new ManifestParseException("dependencies 必须是 JSON 对象: " + file);
            }
            Iterator<Map.Entry<String, JsonNode>> fields = depsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String depId = entry.getKey();
                if (!depId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
                    throw new ManifestParseException("依赖 id 非法: " + depId);
                }
                if (depId.equals(id)) {
                    throw new ManifestParseException(RejectReason.CIRCULAR_DEPENDENCY, id,
                            "扩展不能依赖自身: " + id);
                }
                if (!entry.getValue().isTextual()) {
                    throw new ManifestParseException("dependencies." + depId + " 必须是版本区间字符串: " + file);
                }
                try {
                    dependencies.put(depId, VersionRange.parse(entry.getValue().asText()));
                } catch (IllegalArgumentException ex) {
                    throw new ManifestParseException("dependencies." + depId + " 版本区间非法: " + ex.getMessage());
                }
            }
        }

        return new Manifest(id, name, version, apiVersion, className, defaults, dependencies);
    }

    private String requiredText(JsonNode root, String field, Path file) throws ManifestParseException {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new ManifestParseException("缺少必填文本字段 " + field + ": " + file);
        }
        return node.asText().trim();
    }

    private String textOr(JsonNode root, String field, String fallback) {
        JsonNode node = root.get(field);
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText().trim() : fallback;
    }
}
