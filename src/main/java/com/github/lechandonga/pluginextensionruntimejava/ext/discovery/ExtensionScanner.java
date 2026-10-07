package com.github.lechandonga.pluginextensionruntimejava.ext.discovery;

import com.github.lechandonga.pluginextensionruntimejava.ext.model.Manifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描本地扩展根目录：每个一级子目录视为一个扩展来源，读取其中的 extension.json。
 */
public class ExtensionScanner {

    private static final Logger log = LoggerFactory.getLogger(ExtensionScanner.class);

    private final ManifestParser parser = new ManifestParser();

    public record ScanResult(Map<String, ScannedExtension> valid, List<ManifestFailure> failures) {
    }

    public record ScannedExtension(Manifest manifest, Path directory, ExtensionClassLoader classLoader) implements AutoCloseable {
        @Override
        public void close() {
            try {
                classLoader.close();
            } catch (Exception ex) {
                log.debug("关闭扩展类加载器失败: {}", manifest.id(), ex);
            }
        }
    }

    public record ManifestFailure(String declaredId, Path directory, ManifestParseException error) {
    }

    public ScanResult scan(Path extensionsRoot) {
        Map<String, ScannedExtension> valid = new LinkedHashMap<>();
        List<ManifestFailure> failures = new ArrayList<>();
        if (extensionsRoot == null || !Files.isDirectory(extensionsRoot)) {
            log.warn("扩展根目录不存在，跳过扫描: {}", extensionsRoot);
            return new ScanResult(valid, failures);
        }
        List<Path> dirs;
        try (var stream = Files.list(extensionsRoot)) {
            dirs = stream.filter(Files::isDirectory).sorted().toList();
        } catch (Exception ex) {
            log.error("扫描扩展根目录失败: {}", extensionsRoot, ex);
            return new ScanResult(valid, failures);
        }
        for (Path dir : dirs) {
            try {
                Manifest manifest = parser.parse(dir);
                ExtensionClassLoader classLoader = ExtensionClassLoader.create(dir);
                if (valid.containsKey(manifest.id())) {
                    classLoader.close();
                    ManifestParseException dup = new ManifestParseException(
                            com.github.lechandonga.pluginextensionruntimejava.ext.model.RejectReason.DUPLICATE_ID,
                            "扩展 id '" + manifest.id() + "' 与目录 "
                                    + valid.get(manifest.id()).directory() + " 中的扩展重复");
                    failures.add(new ManifestFailure(manifest.id(), dir, dup));
                } else {
                    valid.put(manifest.id(), new ScannedExtension(manifest, dir, classLoader));
                }
            } catch (ManifestParseException ex) {
                log.warn("扩展目录 {} 的清单被拒绝 [{}]: {}", dir, ex.reason(), ex.getMessage());
                failures.add(new ManifestFailure(bestEffortId(dir), dir, ex));
            } catch (Exception ex) {
                log.warn("扩展目录 {} 的类路径准备失败: {}", dir, ex.getMessage());
                failures.add(new ManifestFailure(bestEffortId(dir), dir,
                        new ManifestParseException(ex.getMessage())));
            }
        }
        return new ScanResult(valid, failures);
    }

    private String bestEffortId(Path dir) {
        try {
            return parser.parse(dir).id();
        } catch (Exception ignore) {
            if (ignore instanceof ManifestParseException mpe && mpe.declaredId() != null) {
                return mpe.declaredId();
            }
            try {
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(parser.manifestPath(dir).toFile());
                if (node != null && node.has("id") && node.get("id").isTextual()) {
                    return node.get("id").asText();
                }
            } catch (Exception ignored) {
            }
            return dir.getFileName().toString();
        }
    }
}
