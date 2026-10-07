package com.github.lechandonga.pluginextensionruntimejava;

import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 框架装配：创建 ExtensionManager，并在启动时扫描本地扩展目录。
 * 外部配置覆盖通过 {@code extension.<extensionId>.<key>} 形式注入。
 */
@Configuration
public class ExtensionFrameworkConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ExtensionFrameworkConfiguration.class);

    @Bean
    public ExtensionManager extensionManager(Environment environment,
                                             @Value("${extensions.root:extensions}") String extensionsRoot) {
        Map<String, String> overrides = new HashMap<>();
        if (environment instanceof org.springframework.core.env.ConfigurableEnvironment configurable) {
            configurable.getPropertySources().forEach(source -> {
                if (source instanceof org.springframework.core.env.EnumerablePropertySource<?> enumerable) {
                    for (String name : enumerable.getPropertyNames()) {
                        if (name.startsWith("extension.")) {
                            overrides.put(name.substring("extension.".length()),
                                    configurable.getProperty(name));
                        }
                    }
                }
            });
        }
        ExtensionManager manager = new ExtensionManager(overrides);
        Path root = Path.of(extensionsRoot);
        if (Files.isDirectory(root)) {
            manager.loadAll(root).forEach(info ->
                    log.info("扩展加载结果: id={} version={} state={} reason={} source={}",
                            info.id(), info.version(), info.state(), info.rejectionReason(), info.source()));
        } else {
            log.info("扩展目录 {} 不存在，跳过启动加载", root.toAbsolutePath());
        }
        return manager;
    }
}
