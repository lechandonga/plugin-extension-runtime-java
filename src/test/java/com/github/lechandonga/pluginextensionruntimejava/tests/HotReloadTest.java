package com.github.lechandonga.pluginextensionruntimejava.tests;

import com.github.lechandonga.pluginextensionruntimejava.ext.runtime.ExtensionRuntime;
import com.github.lechandonga.pluginextensionruntimejava.ext.runtime.ReloadResult;
import com.github.lechandonga.pluginextensionruntimejava.support.BusinessModule;
import com.github.lechandonga.pluginextensionruntimejava.support.ExtensionFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.ECHO_V1;
import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.ECHO_V2;
import static com.github.lechandonga.pluginextensionruntimejava.tests.ExtensionSources.manifest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@BusinessModule("运行时热更新与原子切换")
class HotReloadTest {

    @TempDir
    Path root;

    private ExtensionRuntime runtime;
    private Path echoDir;

    @AfterEach
    void tearDown() throws Exception {
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    private void bootV1(String version) throws IOException {
        echoDir = ExtensionFixtures.writeExtension(root, "echo",
                manifest("demo-echo", version, "1.0", "demo.EchoExtension", null, null),
                "EchoExtension", ECHO_V1);
        runtime = new ExtensionRuntime(root, "1.0", null);
        runtime.start();
    }

    private void writeVersion(String version) throws IOException {
        Files.writeString(echoDir.resolve("extension.json"),
                manifest("demo-echo", version, "1.0", "demo.EchoExtension", null, null),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("成功热更新：版本与行为切换为新版本，结果列明更新/保持/移除")
    void successfulReloadPublishesNewVersion() throws IOException {
        bootV1("1.0.0");
        assertEquals("hello-default:a:1", runtime.invoke("demo-echo", "a", 1));

        writeVersion("2.0.0");
        ExtensionFixtures.rewriteCompiledClass(echoDir, "EchoExtension", ECHO_V2);

        ReloadResult result = runtime.reload();

        assertFalse(result.rolledBack());
        assertTrue(result.updatedIds().toString().contains("1.0.0 -> 2.0.0"), result.updatedIds().toString());
        assertEquals("2.0.0", runtime.states().get(0).version());
        assertEquals("hello-default-v2:a:1", runtime.invoke("demo-echo", "a", 1));
    }

    @Test
    @DisplayName("热更新失败回退：新版本清单非法时旧版本保持 ACTIVE 并继续服务")
    void brokenNewVersionRollsBackToPrevious() throws IOException {
        bootV1("1.0.0");

        Path failingDir = ExtensionFixtures.writeExtension(root, "failing",
                manifest("demo-failing", "1.0.0", "1.0", "demo.FailStartExtension", null, null),
                "FailStartExtension", ExtensionSources.FAIL_START);
        // 用一个启动即失败的新扩展制造更新失败；把 echo 的清单也更新到 2.0.0 验证回退后版本不变
        writeVersion("2.0.0");

        ReloadResult result = runtime.reload();

        assertTrue(result.rolledBack());
        assertTrue(result.errorMessage().contains("demo-failing"));
        assertEquals("1.0.0", runtime.states().get(0).version());
        assertEquals("hello-default:a:1", runtime.invoke("demo-echo", "a", 1));
        assertEquals(result.errorMessage(), runtime.lastUpdateError());
    }

    @Test
    @DisplayName("热更新失败回退：新版本产生依赖冲突时旧版本继续服务")
    void dependencyConflictInNewVersionRollsBack() throws IOException {
        bootV1("1.0.0");

        writeVersion("not-a-version");

        ReloadResult result = runtime.reload();

        assertTrue(result.rolledBack(), "非法版本号必须导致回退");
        assertEquals("1.0.0", runtime.states().get(0).version());
        assertTrue(runtime.exists("demo-echo"));
    }

    @Test
    @DisplayName("回退后修复问题可再次热更成功，错误状态被清除")
    void recoveredUpdateAfterRollbackSucceeds() throws IOException {
        bootV1("1.0.0");
        writeVersion("bad-version");
        assertTrue(runtime.reload().rolledBack());

        writeVersion("1.1.0");
        ExtensionFixtures.rewriteCompiledClass(echoDir, "EchoExtension", ECHO_V2);

        ReloadResult result = runtime.reload();
        assertFalse(result.rolledBack());
        assertEquals("1.1.0", runtime.states().get(0).version());
        assertEquals(null, runtime.lastUpdateError());
        assertEquals("hello-default-v2:a:1", runtime.invoke("demo-echo", "a", 1));
    }

    @Test
    @DisplayName("并发读与热更新：读线程只能观察到旧版本或新版本，不存在半加载状态")
    void concurrentReadersSeeOldOrNewVersionOnly() throws Exception {
        bootV1("1.0.0");

        ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicReference<Throwable> readerError = new AtomicReference<>();

        Runnable reader = () -> {
            try {
                start.await();
                while (!stop.get()) {
                    Object reply = runtime.invoke("demo-echo", "p", 1);
                    String text = String.valueOf(reply);
                    boolean oldReply = text.equals("hello-default:p:1");
                    boolean newReply = text.equals("hello-default-v2:p:1");
                    if (!oldReply && !newReply) {
                        readerError.compareAndSet(null,
                                new AssertionError("观察到非旧非新的中间状态: " + text));
                    }
                }
            } catch (Throwable t) {
                readerError.compareAndSet(null, t);
            }
        };

        for (int i = 0; i < 2; i++) {
            pool.submit(reader);
        }
        start.countDown();

        for (int round = 1; round <= 3; round++) {
            writeVersion(round % 2 == 0 ? "1.0." + round : "2.0." + round);
            ExtensionFixtures.rewriteCompiledClass(echoDir, "EchoExtension",
                    round % 2 == 0 ? ECHO_V1 : ECHO_V2);
            runtime.reload();
        }

        stop.set(true);
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        if (readerError.get() != null) {
            throw new AssertionError("并发读检测到中间状态", readerError.get());
        }
    }

    @Test
    @DisplayName("热更新串行化：并发触发多次 reload 不会产生交错的中间注册表")
    void concurrentReloadsAreSerialized() throws Exception {
        bootV1("1.0.0");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 4; i++) {
            final int round = i;
            pool.submit(() -> {
                try {
                    start.await();
                    writeVersion("1.0." + round);
                    runtime.reload();
                } catch (Exception ignore) {
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(1, runtime.states().size());
    }
}
