package com.github.lechandonga.pluginextensionruntimejava.tests.extension;

import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionInfo;
import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionManager;
import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionState;
import com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.FlakyExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.ExplodingExtension;
import static com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.GoodExtension;
import static com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.manifest;
import static com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.writeExtension;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务场景【热更新与故障隔离】：并发读写一致性、更新失败回退、运行期异常隔离。
 */
class HotUpdateTest {

    private static final Logger log = LoggerFactory.getLogger(HotUpdateTest.class);

    @TempDir
    Path root;

    @BeforeAll
    static void businessScope() {
        log.info("==== 业务覆盖: 热更新与故障隔离 (并发读写一致性/更新失败回退/运行期异常隔离) ====");
    }

    @Test
    void failedUpdateRollsBackToPreviousVersion() throws IOException {
        log.info("用例: 热更新新版本 start 抛错 -> 回退保留旧版本继续服务");
        writeExtension(root, "svc", manifest("svc", "1.0.0", GoodExtension.class));
        ExtensionManager manager = new ExtensionManager();
        manager.loadAll(root);
        assertThat(manager.registry().get("svc")).isPresent()
                .get().satisfies(h -> assertThat(h.manifest().version()).isEqualTo("1.0.0"));

        writeExtension(root, "svc", manifest("svc", "2.0.0", ExplodingExtension.class));
        ExtensionInfo result = manager.update(root.resolve("svc"));

        assertThat(result.version()).isEqualTo("1.0.0");
        assertThat(result.state()).isEqualTo(ExtensionState.ACTIVE);
        assertThat(manager.registry().isActive("svc")).isTrue();
        assertThat(manager.registry().get("svc")).isPresent()
                .get().satisfies(h -> assertThat(h.manifest().version()).isEqualTo("1.0.0"));
    }

    @Test
    void successfulUpdateSwapsVersionAtomically() throws IOException {
        log.info("用例: 热更新成功 -> 原子切换到新版本，旧实例 stop 被回调");
        GoodExtension.STOP_COUNT.set(0);
        writeExtension(root, "svc", manifest("svc", "1.0.0", GoodExtension.class));
        ExtensionManager manager = new ExtensionManager();
        manager.loadAll(root);

        writeExtension(root, "svc", manifest("svc", "2.0.0", GoodExtension.class));
        ExtensionInfo result = manager.update(root.resolve("svc"));

        assertThat(result.version()).isEqualTo("2.0.0");
        assertThat(manager.registry().get("svc")).isPresent()
                .get().satisfies(h -> assertThat(h.manifest().version()).isEqualTo("2.0.0"));
        assertThat(GoodExtension.STOP_COUNT.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void concurrentReadersNeverObservePartialState() throws Exception {
        log.info("用例: 更新期间并发读取 -> 只能观察到完整的旧版本或新版本");
        writeExtension(root, "svc", manifest("svc", "1.0.0", GoodExtension.class));
        ExtensionManager manager = new ExtensionManager();
        manager.loadAll(root);

        int readers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> observed = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < readers; i++) {
            pool.submit(() -> {
                await(start);
                for (int j = 0; j < 500; j++) {
                    manager.registry().get("svc").ifPresent(h -> observed.add(h.manifest().version()));
                }
            });
        }
        pool.submit(() -> {
            await(start);
            for (int v = 2; v <= 20; v++) {
                try {
                    writeExtension(root, "svc", manifest("svc", v + ".0.0", GoodExtension.class));
                    manager.update(root.resolve("svc"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(observed).isNotEmpty().allSatisfy(version -> {
            assertThat(version).matches("\\d+\\.0\\.0");
            int major = Integer.parseInt(version.split("\\.")[0]);
            assertThat(major).isBetween(1, 20);
        });
        assertThat(manager.registry().get("svc")).isPresent()
                .get().satisfies(h -> assertThat(h.manifest().version()).isEqualTo("20.0.0"));
        log.info("并发读取样本 {} 条，观测到的版本集合: {}", observed.size(), Set.copyOf(observed));
    }

    @Test
    void runtimeExceptionIsIsolatedPerExtension() throws IOException {
        log.info("用例: 扩展运行期抛错 -> 异常被隔离返回 null，其它扩展与框架正常");
        writeExtension(root, "flaky", manifest("flaky", "1.0.0", FlakyExtension.class));
        writeExtension(root, "stable", manifest("stable", "1.0.0", GoodExtension.class));
        ExtensionManager manager = new ExtensionManager();
        manager.loadAll(root);

        String result = manager.invoke("flaky", ext -> ((FlakyExtension) ext).doWork());

        assertThat(result).isNull();
        assertThat(manager.registry().isActive("stable")).isTrue();
        String stableResult = manager.invoke("stable", ext -> "ok");
        assertThat(stableResult).isEqualTo("ok");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
