package com.github.lechandonga.pluginextensionruntimejava.extension;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 解析扩展目录中的 extension-manifest.json。
 */
public class ManifestParser {

    public static final String MANIFEST_FILE_NAME = "extension-manifest.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExtensionManifest parse(Path extensionDir) throws ExtensionLoadException {
        Path manifestFile = extensionDir.resolve(MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifestFile)) {
            throw new ExtensionLoadException(RejectionReason.INVALID_MANIFEST,
                    "清单文件缺失: " + manifestFile);
        }
        ExtensionManifest manifest;
        try {
            manifest = objectMapper.readValue(manifestFile.toFile(), ExtensionManifest.class);
        } catch (IOException e) {
            throw new ExtensionLoadException(RejectionReason.INVALID_MANIFEST,
                    "清单 JSON 非法: " + manifestFile + " (" + e.getMessage() + ")", e);
        }
        validate(manifest, manifestFile);
        return manifest;
    }

    private void validate(ExtensionManifest manifest, Path file) throws ExtensionLoadException {
        if (isBlank(manifest.id()) || isBlank(manifest.version())
                || isBlank(manifest.apiVersion()) || isBlank(manifest.implementation())) {
            throw new ExtensionLoadException(RejectionReason.INVALID_MANIFEST,
                    "清单缺少必填字段(id/version/apiVersion/implementation): " + file);
        }
        try {
            VersionRange.Version.parse(manifest.version());
        } catch (IllegalArgumentException e) {
            throw new ExtensionLoadException(RejectionReason.INVALID_MANIFEST,
                    "清单版本号非法: " + manifest.version(), e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
