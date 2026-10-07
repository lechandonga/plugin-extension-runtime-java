package com.github.lechandonga.pluginextensionruntimejava.tests;

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
import java.nio.file.Path;

import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.ECHO_V1;
import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.UPPER;
import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.manifest;
import static org.junit.jupiter.api.Assertions.assertEquals;

@BusinessModule("依赖装配与版本兼容")
class DependencyAssemblyTest {

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

    private ExtensionStatus status(String id) {
        return runtime.states().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow().status();
    }

    private RejectReason reason(String id) {
        return runtime.states().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow().rejectReason();
    }

    @Test
    @DisplayName("依赖满足且版本在区间内：按拓扑序启动，上游可调用下游")
    void satisfiedDependenciesStartInTopologicalOrder() throws IOException {
        ExtensionFixtures.writeExtension(root, "echo",
                manifest("demo-echo", "1.3.0", "1.0", "demo.EchoExtension", null, null),
                "EchoExtension", ECHO_V1);
        ExtensionFixtures.writeExtension(root, "upper",
                manifest("demo-upper", "1.0.0", "1.0", "demo.UpperExtension",
                        "{\"demo-echo\":\"[1.0,2.0)\"}", null),
                "UpperExtension", UPPER);

        boot();

        assertEquals(ExtensionStatus.ACTIVE, status("demo-echo"));
        assertEquals(ExtensionStatus.ACTIVE, status("demo-upper"));
        assertEquals("upper[hello-default:ping:from-upper]:HELLO", runtime.invoke("demo-upper", "x", "hello"));
    }

    @Test
    @DisplayName("缺失依赖：依赖扩展不存在，原因 MISSING_DEPENDENCY 且细节指明依赖 id")
    void missingDependencyIsRejected() throws IOException {
        ExtensionFixtures.writeExtension(root, "upper",
                manifest("demo-upper", "1.0.0", "1.0", "demo.UpperExtension",
                        "{\"demo-echo\":\"[1.0,2.0)\"}", null),
                "UpperExtension", UPPER);

        boot();

        assertEquals(ExtensionStatus.REJECTED, status("demo-upper"));
        assertEquals(RejectReason.MISSING_DEPENDENCY, reason("demo-upper"));
    }

    @Test
    @DisplayName("版本区间冲突：依赖实际版本不在声明区间，原因 VERSION_RANGE_CONFLICT")
    void versionRangeConflictIsRejected() throws IOException {
        ExtensionFixtures.writeExtension(root, "echo",
                manifest("demo-echo", "2.1.0", "1.0", "demo.EchoExtension", null, null),
                "EchoExtension", ECHO_V1);
        ExtensionFixtures.writeExtension(root, "upper",
                manifest("demo-upper", "1.0.0", "1.0", "demo.UpperExtension",
                        "{\"demo-echo\":\"[1.0,2.0)\"}", null),
                "UpperExtension", UPPER);

        boot();

        assertEquals(ExtensionStatus.ACTIVE, status("demo-echo"));
        assertEquals(ExtensionStatus.REJECTED, status("demo-upper"));
        assertEquals(RejectReason.VERSION_RANGE_CONFLICT, reason("demo-upper"));
    }

    @Test
    @DisplayName("循环依赖：A->B->A 两个扩展均被拒绝，原因 CIRCULAR_DEPENDENCY")
    void circularDependencyIsRejected() throws IOException {
        ExtensionFixtures.writeExtension(root, "a",
                manifest("ext-a", "1.0.0", "1.0", "demo.EchoExtension",
                        "{\"ext-b\":\"[1.0,2.0)\"}", null),
                "EchoExtension", ECHO_V1);
        ExtensionFixtures.writeExtension(root, "b",
                manifest("ext-b", "1.0.0", "1.0", "demo.EchoExtension",
                        "{\"ext-a\":\"[1.0,2.0)\"}", null),
                "EchoExtension", ECHO_V1);

        boot();

        assertEquals(RejectReason.CIRCULAR_DEPENDENCY, reason("ext-a"));
        assertEquals(RejectReason.CIRCULAR_DEPENDENCY, reason("ext-b"));
    }

    @Test
    @DisplayName("依赖启动失败会传导：上游以 MISSING_DEPENDENCY 被拒，不相关扩展照常 ACTIVE")
    void failurePropagatesToDependentsOnly() throws IOException {
        ExtensionFixtures.writeExtension(root, "echo",
                manifest("demo-echo", "1.0.0", "1.0", "demo.FailStartExtension", null, null),
                "FailStartExtension", ExtensionSources.FAIL_START);
        ExtensionFixtures.writeExtension(root, "upper",
                manifest("demo-upper", "1.0.0", "1.0", "demo.UpperExtension",
                        "{\"demo-echo\":\"[1.0,2.0)\"}", null),
                "UpperExtension", UPPER);
        ExtensionFixtures.writeExtension(root, "solo",
                manifest("demo-solo", "1.0.0", "1.0", "demo.EchoExtension", null, null),
                "EchoExtension", ECHO_V1);

        boot();

        assertEquals(RejectReason.START_FAILED, reason("demo-echo"));
        assertEquals(RejectReason.MISSING_DEPENDENCY, reason("demo-upper"));
        assertEquals(ExtensionStatus.ACTIVE, status("demo-solo"));
    }

    @Test
    @DisplayName("自依赖在清单阶段即被拒绝，原因 CIRCULAR_DEPENDENCY")
    void selfDependencyIsRejected() throws IOException {
        ExtensionFixtures.writeExtension(root, "self",
                manifest("ext-self", "1.0.0", "1.0", "demo.EchoExtension",
                        "{\"ext-self\":\"[1.0,2.0)\"}", null),
                "EchoExtension", ECHO_V1);

        boot();

        assertEquals(RejectReason.CIRCULAR_DEPENDENCY, reason("ext-self"));
    }
}
