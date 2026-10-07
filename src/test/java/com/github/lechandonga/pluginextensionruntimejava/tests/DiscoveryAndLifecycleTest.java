package com.github.lechandonga.pluginextensionruntimejava.tests;

import com.github.lechandonga.pluginextensionruntimejava.ext.model.ExtensionStateView;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.ExtensionStatus;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.RejectReason;
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
import java.util.List;

import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.ECHO_V1;
import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.manifest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@BusinessModule("扩展发现与生命周期管理")
class DiscoveryAndLifecycleTest {

    @TempDir
    Path root;

    private ExtensionRuntime runtime;

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    private ExtensionRuntime boot() {
        runtime = new ExtensionRuntime(root, "1.0", null);
        runtime.start();
        return runtime;
    }

    private ExtensionStateView view(String id) {
        return runtime.states().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("正常扩展加载后可查询状态、版本与来源目录")
    void validExtensionIsActiveWithVersionAndSource() throws IOException {
        Path dir = ExtensionFixtures.writeExtension(root, "echo",
                manifest("demo-echo", "1.2.0", "1.0", "demo.EchoExtension", null, null),
                "EchoExtension", ECHO_V1);

        boot();

        ExtensionStateView state = view("demo-echo");
        assertEquals(ExtensionStatus.ACTIVE, state.status());
        assertEquals("1.2.0", state.version());
        assertEquals("1.0", state.apiVersion());
        assertEquals(dir, state.source());
        assertNotNull(state.loadedAt());
        assertNull(state.rejectReason());
        assertEquals("hello-default:go:payload", runtime.invoke("demo-echo", "go", "payload"));
    }

    @Test
    @DisplayName("清单非法：JSON 损坏，拒绝原因为 INVALID_MANIFEST")
    void invalidManifestIsRejectedWithDistinctReason() throws IOException {
        Path dir = Files.createDirectories(root.resolve("broken"));
        Files.writeString(dir.resolve("extension.json"), "{ this is not json ");
        Files.createDirectories(dir.resolve("classes"));

        boot();

        ExtensionStateView state = runtime.states().stream()
                .filter(v -> v.source().equals(dir)).findFirst().orElseThrow();
        assertEquals(ExtensionStatus.REJECTED, state.status());
        assertEquals(RejectReason.INVALID_MANIFEST, state.rejectReason());
        assertTrue(state.rejectDetail().contains("JSON"));
    }

    @Test
    @DisplayName("清单非法：缺少必填字段 className，拒绝原因为 INVALID_MANIFEST 且指明字段")
    void missingRequiredFieldIsRejected() throws IOException {
        ExtensionFixtures.writeExtension(root, "nofield",
                "{\"id\":\"no-classname\",\"version\":\"1.0.0\",\"apiVersion\":\"1.0\"}",
                "EchoExtension", ECHO_V1);

        boot();

        ExtensionStateView state = view("no-classname");
        assertEquals(RejectReason.INVALID_MANIFEST, state.rejectReason());
        assertTrue(state.rejectDetail().contains("className"), "应指明缺失字段: " + state.rejectDetail());
    }

    @Test
    @DisplayName("扩展标识重复：第二个扩展被拒绝，原因 DUPLICATE_ID")
    void duplicateIdIsRejected() throws IOException {
        String manifestJson = manifest("demo-echo", "1.0.0", "1.0", "demo.EchoExtension", null, null);
        ExtensionFixtures.writeExtension(root, "echo-a", manifestJson, "EchoExtension", ECHO_V1);
        ExtensionFixtures.writeExtension(root, "echo-b", manifestJson, "EchoExtension", ECHO_V1);

        boot();

        assertEquals(ExtensionStatus.ACTIVE, view("demo-echo").status());
        List<ExtensionStateView> rejected = runtime.states().stream()
                .filter(v -> v.status() == ExtensionStatus.REJECTED).toList();
        assertEquals(1, rejected.size());
        assertEquals(RejectReason.DUPLICATE_ID, rejected.get(0).rejectReason());
        assertTrue(rejected.get(0).rejectDetail().contains("demo-echo"));
    }

    @Test
    @DisplayName("接口版本不兼容：更高次版本与不同主版本均被拒绝，原因 INCOMPATIBLE_API_VERSION")
    void incompatibleApiVersionIsRejected() throws IOException {
        ExtensionFixtures.writeExtension(root, "too-new",
                manifest("ext-too-new", "1.0.0", "1.5", "demo.EchoExtension", null, null),
                "EchoExtension", ECHO_V1);
        ExtensionFixtures.writeExtension(root, "major2",
                manifest("ext-major2", "1.0.0", "2.0", "demo.EchoExtension", null, null),
                "EchoExtension", ECHO_V1);

        boot();

        for (ExtensionStateView state : runtime.states()) {
            assertEquals(ExtensionStatus.REJECTED, state.status());
            assertEquals(RejectReason.INCOMPATIBLE_API_VERSION, state.rejectReason());
        }
    }

    @Test
    @DisplayName("故障隔离：一个扩展清单非法不影响另一个扩展加载和使用")
    void oneFailureDoesNotAffectOthers() throws IOException {
        Path broken = Files.createDirectories(root.resolve("broken"));
        Files.writeString(broken.resolve("extension.json"), "{ broken ");
        Files.createDirectories(broken.resolve("classes"));
        ExtensionFixtures.writeExtension(root, "echo",
                manifest("demo-echo", "1.0.0", "1.0", "demo.EchoExtension", null, null),
                "EchoExtension", ECHO_V1);

        boot();

        assertEquals(2, runtime.states().size());
        assertEquals(1, runtime.states().stream().filter(v -> v.status() == ExtensionStatus.ACTIVE).count());
        assertEquals("hello-default:ok:1", runtime.invoke("demo-echo", "ok", 1));
    }

    @Test
    @DisplayName("实现类加载失败：类不存在时原因 CLASS_LOAD_FAILED，其它扩展照常 ACTIVE")
    void classLoadFailureIsIsolated() throws IOException {
        ExtensionFixtures.writeExtension(root, "ghost",
                manifest("ext-ghost", "1.0.0", "1.0", "demo.NotExist", null, null),
                "EchoExtension", ECHO_V1);
        ExtensionFixtures.writeExtension(root, "echo",
                manifest("demo-echo", "1.0.0", "1.0", "demo.EchoExtension", null, null),
                "EchoExtension", ECHO_V1);

        boot();

        ExtensionStateView ghost = view("ext-ghost");
        assertEquals(ExtensionStatus.REJECTED, ghost.status());
        assertEquals(RejectReason.CLASS_LOAD_FAILED, ghost.rejectReason());
        assertEquals(ExtensionStatus.ACTIVE, view("demo-echo").status());
    }
}
