package com.github.lechandonga.pluginextensionruntimejava.tests;

import com.github.lechandonga.pluginextensionruntimejava.ext.model.ExtensionStateView;
import com.github.lechandonga.pluginextensionruntimejava.ext.runtime.ExtensionInvocationException;
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
import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.FAIL_INVOKE;
import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.manifest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@BusinessModule("运行期故障隔离")
class FaultIsolationTest {

    @TempDir
    Path root;

    private ExtensionRuntime runtime;

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    @Test
    @DisplayName("扩展运行抛错只影响本次调用：错误被隔离、记录，其它扩展与自身后续调用不受影响")
    void runtimeErrorIsContainedAndRecorded() throws IOException {
        ExtensionFixtures.writeExtension(root, "fail",
                manifest("ext-fail", "1.0.0", "1.0", "demo.FailInvokeExtension", null, null),
                "FailInvokeExtension", FAIL_INVOKE);
        ExtensionFixtures.writeExtension(root, "echo",
                manifest("demo-echo", "1.0.0", "1.0", "demo.EchoExtension", null, null),
                "EchoExtension", ECHO_V1);
        runtime = new ExtensionRuntime(root, "1.0", null);
        runtime.start();

        assertThrows(ExtensionInvocationException.class,
                () -> runtime.invoke("ext-fail", "x", null));

        // 其它扩展照常服务
        assertEquals("hello-default:y:2", runtime.invoke("demo-echo", "y", 2));
        // 出错扩展本身仍 ACTIVE 且可继续服务（隔离不摘除）
        ExtensionStateView failView = runtime.states().stream()
                .filter(v -> v.id().equals("ext-fail")).findFirst().orElseThrow();
        assertEquals(com.github.lechandonga.pluginextensionruntimejava.ext.model.ExtensionStatus.ACTIVE,
                failView.status());
        assertNotNull(failView.lastRuntimeError());
        assertTrue(failView.lastRuntimeError().contains("故意在运行期抛错"));
        assertThrows(ExtensionInvocationException.class,
                () -> runtime.invoke("ext-fail", "x", null));
    }

    @Test
    @DisplayName("调用不存在的扩展抛出可区分异常，不影响服务整体")
    void invokeUnknownExtensionThrows() throws IOException {
        ExtensionFixtures.writeExtension(root, "echo",
                manifest("demo-echo", "1.0.0", "1.0", "demo.EchoExtension", null, null),
                "EchoExtension", ECHO_V1);
        runtime = new ExtensionRuntime(root, "1.0", null);
        runtime.start();

        assertThrows(ExtensionInvocationException.class, () -> runtime.invoke("no-such", "x", null));
        assertEquals("hello-default:y:2", runtime.invoke("demo-echo", "y", 2));
    }
}
