package com.github.lechandonga.pluginextensionruntimejava.ext.spring;

import com.github.lechandonga.pluginextensionruntimejava.ext.runtime.ExtensionRuntime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 扩展运行时的 Spring 装配。配置 extensions.root 后自动生效。
 */
@Configuration
@EnableConfigurationProperties(ExtensionProperties.class)
public class ExtensionRuntimeConfiguration {

    @Bean
    public ExtensionRuntime extensionRuntime(ExtensionProperties properties) {
        ExtensionRuntime runtime = new ExtensionRuntime(
                properties.getRoot(), properties.getApiVersion(), properties.getOverrideFile());
        runtime.start();
        return runtime;
    }

    @Bean
    @ConditionalOnProperty(prefix = "extensions", name = "watch", havingValue = "true")
    public ExtensionDirectoryWatcher extensionDirectoryWatcher(ExtensionRuntime runtime,
                                                                ExtensionProperties properties) {
        ExtensionDirectoryWatcher watcher =
                new ExtensionDirectoryWatcher(runtime, properties.getRoot());
        watcher.start();
        return watcher;
    }
}
