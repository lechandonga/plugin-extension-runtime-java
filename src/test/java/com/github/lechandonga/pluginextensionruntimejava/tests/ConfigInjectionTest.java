package com.github.lechandonga.pluginextensionruntimejava.tests;

import com.github.lechandonga.pluginextensionruntimejava.ext.config.ConfigResolution;
import com.github.lechandonga.pluginextensionruntimejava.ext.runtime.ExtensionRuntime;
import com.github.lechandonga.pluginextensionruntimejava.support.BusinessModule;
import com.github.lechandonga.pluginextensionruntimejava.support.ExtensionFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.ECHO_V1;
import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.manifest;
import static org.junit.jupiter.api.Assertions.assertEquals;

@BusinessModule("扩展配置注入与优先级")
class ConfigInjectionTest {

    @TempDir
    Path root;

    private ExtensionRuntime runtime;
    private Path overrideFile;

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.shutdown();
        }
        System.clearProperty("ext.demo-echo.greeting");
    }

    @Test
    @DisplayName("优先级：系统属性 > 外部覆盖文件 > 清单默认值，且每个 key 的来源可查询")
    void configPriorityAndSourcesAreExplainable() throws IOException {
        ExtensionFixtures.writeExtension(root, "echo",
                manifest("demo-echo", "1.0.0", "1.0", "demo.EchoExtension", null,
                        "{\"greeting\":\"manifest-hello\",\"onlyDefault\":\"d\"}"),
                "EchoExtension", ECHO_V1);
        overrideFile = root.resolve("override.properties");
        Files.writeString(overrideFile,
                "ext.demo-echo.greeting=file-hello\n"
                        + "ext.demo-echo.fileOnly=from-file\n");
        System.setProperty("ext.demo-echo.greeting", "system-hello");

        runtime = new ExtensionRuntime(root, "1.0", overrideFile);
        runtime.start();

        ConfigResolution config = runtime.configOf("demo-echo");
        assertEquals("system-hello", config.values().get("greeting"));
        assertEquals(ConfigResolution.SOURCE_SYSTEM_PROPERTY, config.sources().get("greeting"));
        assertEquals("from-file", config.values().get("fileOnly"));
        assertEquals(ConfigResolution.overrideFileSource(overrideFile.toString()),
                config.sources().get("fileOnly"));
        assertEquals("d", config.values().get("onlyDefault"));
        assertEquals(ConfigResolution.SOURCE_MANIFEST_DEFAULT, config.sources().get("onlyDefault"));

        assertEquals("system-hello:greet:1", runtime.invoke("demo-echo", "greet", 1));
    }

    @Test
    @DisplayName("命名空间隔离：外部配置只注入同 id 扩展，不能覆盖其它扩展")
    void configNamespacesAreIsolated() throws IOException {
        ExtensionFixtures.writeExtension(root, "echo-a",
                manifest("ext-a", "1.0.0", "1.0", "demo.EchoExtension", null,
                        "{\"greeting\":\"a-default\"}"),
                "EchoExtension", ECHO_V1);
        ExtensionFixtures.writeExtension(root, "echo-b",
                manifest("ext-b", "1.0.0", "1.0", "demo.EchoExtension", null,
                        "{\"greeting\":\"b-default\"}"),
                "EchoExtension", ECHO_V1);
        overrideFile = root.resolve("override.properties");
        Files.writeString(overrideFile, "ext.ext-a.greeting=a-from-file\n");

        runtime = new ExtensionRuntime(root, "1.0", overrideFile);
        runtime.start();

        assertEquals("a-from-file", runtime.configOf("ext-a").values().get("greeting"));
        assertEquals("b-default", runtime.configOf("ext-b").values().get("greeting"));
    }
}
