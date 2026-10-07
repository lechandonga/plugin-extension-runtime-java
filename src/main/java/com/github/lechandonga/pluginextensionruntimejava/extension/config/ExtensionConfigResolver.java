package com.github.lechandonga.pluginextensionruntimejava.extension.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 解析扩展配置，覆盖优先级确定且可解释（低 → 高）：
 * <ol>
 *   <li>清单内置默认配置 manifest.config</li>
 *   <li>扩展目录下的 config.properties</li>
 *   <li>外部覆盖（前缀 {@code <extensionId>.}，如命令行/环境注入）</li>
 * </ol>
 */
public class ExtensionConfigResolver {

    public static final String CONFIG_FILE_NAME = "config.properties";

    private static final Logger log = LoggerFactory.getLogger(ExtensionConfigResolver.class);

    /**
     * @param manifestDefaults 清单默认配置
     * @param extensionDir     扩展目录（可为 null）
     * @param externalOverrides 外部全量配置，仅取 {@code extensionId.} 前缀项
     */
    public ExtensionConfig resolve(String extensionId,
                                   Map<String, String> manifestDefaults,
                                   Path extensionDir,
                                   Map<String, String> externalOverrides) {
        Map<String, String> merged = new LinkedHashMap<>(manifestDefaults);

        if (extensionDir != null) {
            Path configFile = extensionDir.resolve(CONFIG_FILE_NAME);
            if (Files.isRegularFile(configFile)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(configFile)) {
                    props.load(in);
                } catch (IOException e) {
                    throw new IllegalStateException("读取扩展配置文件失败: " + configFile, e);
                }
                props.stringPropertyNames().forEach(k -> merged.put(k, props.getProperty(k)));
            }
        }

        String prefix = extensionId + ".";
        externalOverrides.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                merged.put(key.substring(prefix.length()), value);
            }
        });

        log.debug("扩展 [{}] 配置解析完成，生效键: {}", extensionId, merged.keySet());
        return new ExtensionConfig(extensionId, merged);
    }
}
