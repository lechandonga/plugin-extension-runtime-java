package com.github.lechandonga.pluginextensionruntimejava.tests.extension;

import com.github.lechandonga.pluginextensionruntimejava.extension.Extension;
import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试支撑：快速写出扩展目录，以及可断言行为的测试扩展实现。
 */
final class ExtensionTestSupport {

    private ExtensionTestSupport() {
    }

    static Path writeExtension(Path root, String dirName, String manifestJson) throws IOException {
        return writeExtension(root, dirName, manifestJson, null);
    }

    static Path writeExtension(Path root, String dirName, String manifestJson, Map<String, String> configProps)
            throws IOException {
        Path dir = Files.createDirectories(root.resolve(dirName));
        Files.writeString(dir.resolve("extension-manifest.json"), manifestJson);
        if (configProps != null) {
            StringBuilder sb = new StringBuilder();
            configProps.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
            Files.writeString(dir.resolve("config.properties"), sb.toString());
        }
        return dir;
    }

    static String manifest(String id, String version, String apiVersion, Class<?> impl,
                           String dependencies, Map<String, String> config) {
        StringBuilder cfg = new StringBuilder("{");
        if (config != null) {
            config.forEach((k, v) -> cfg.append('"').append(k).append("\":\"").append(v).append("\","));
        }
        if (cfg.length() > 1) {
            cfg.setLength(cfg.length() - 1);
        }
        cfg.append('}');
        return """
                {
                  "id": "%s",
                  "version": "%s",
                  "apiVersion": "%s",
                  "implementation": "%s",
                  "dependencies": %s,
                  "config": %s
                }
                """.formatted(id, version, apiVersion, impl.getName(), dependencies, cfg);
    }

    static String manifest(String id, String version, Class<?> impl) {
        return manifest(id, version, "1", impl, "[]", null);
    }

    /** 正常扩展：记录启动次数与收到的配置。 */
    public static class GoodExtension implements Extension {
        public static final AtomicInteger START_COUNT = new AtomicInteger();
        public static final AtomicInteger STOP_COUNT = new AtomicInteger();
        public static final Map<String, String> LAST_CONFIG = new ConcurrentHashMap<>();

        @Override
        public void start(ExtensionContext context) {
            START_COUNT.incrementAndGet();
            LAST_CONFIG.putAll(context.config().asMap());
        }

        @Override
        public void stop() {
            STOP_COUNT.incrementAndGet();
        }
    }

    /** start 即抛错的扩展，用于失败隔离与热更新回退场景。 */
    public static class ExplodingExtension implements Extension {
        @Override
        public void start(ExtensionContext context) {
            throw new IllegalStateException("boom on start");
        }
    }

    /** 运行期会抛错的扩展，用于故障隔离场景。 */
    public static class FlakyExtension implements Extension {
        @Override
        public void start(ExtensionContext context) {
        }

        public String doWork() {
            throw new IllegalStateException("runtime boom");
        }
    }
}
