package com.github.lechandonga.pluginextensionruntimejava.tests.extension;

import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionManager;
import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionState;
import com.github.lechandonga.pluginextensionruntimejava.extension.RejectionReason;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

import static com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.GoodExtension;
import static com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.manifest;
import static com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.writeExtension;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务场景【依赖装配】：缺失依赖、版本区间冲突、循环依赖均被明确拒绝，
 * 依赖顺序正确时装配成功。
 */
class DependencyResolutionTest {

    private static final Logger log = LoggerFactory.getLogger(DependencyResolutionTest.class);

    @TempDir
    Path root;

    @BeforeAll
    static void businessScope() {
        log.info("==== 业务覆盖: 扩展依赖装配 (缺失依赖/版本区间冲突/循环依赖/拓扑顺序装配) ====");
    }

    @Test
    void missingDependencyIsRejected() throws IOException {
        log.info("用例: 依赖不存在 -> REJECTED/MISSING_DEPENDENCY");
        writeExtension(root, "consumer",
                manifest("consumer", "1.0.0", "1", GoodExtension.class,
                        "[{\"id\":\"ghost\",\"version\":\"[1.0,]\"}]", null));

        ExtensionManager manager = new ExtensionManager();
        manager.loadAll(root);

        assertThat(manager.registry().snapshot()).singleElement().satisfies(info -> {
            assertThat(info.state()).isEqualTo(ExtensionState.REJECTED);
            assertThat(info.rejectionReason()).isEqualTo(RejectionReason.MISSING_DEPENDENCY);
        });
    }

    @Test
    void versionRangeConflictIsRejected() throws IOException {
        log.info("用例: 依赖版本区间冲突 -> REJECTED/VERSION_CONFLICT，依赖方自身不启动");
        writeExtension(root, "core", manifest("core", "2.5.0", GoodExtension.class));
        writeExtension(root, "plugin",
                manifest("plugin", "1.0.0", "1", GoodExtension.class,
                        "[{\"id\":\"core\",\"version\":\"[1.0,2.0)\"}]", null));

        ExtensionManager manager = new ExtensionManager();
        manager.loadAll(root);

        assertThat(manager.registry().isActive("core")).isTrue();
        assertThat(manager.registry().snapshot())
                .anySatisfy(info -> {
                    assertThat(info.id()).isEqualTo("plugin");
                    assertThat(info.state()).isEqualTo(ExtensionState.REJECTED);
                    assertThat(info.rejectionReason()).isEqualTo(RejectionReason.VERSION_CONFLICT);
                });
    }

    @Test
    void cyclicDependencyIsRejected() throws IOException {
        log.info("用例: 循环依赖 a<->b -> 双方 REJECTED/CYCLIC_DEPENDENCY，独立扩展不受影响");
        writeExtension(root, "a",
                manifest("a", "1.0.0", "1", GoodExtension.class, "[{\"id\":\"b\"}]", null));
        writeExtension(root, "b",
                manifest("b", "1.0.0", "1", GoodExtension.class, "[{\"id\":\"a\"}]", null));
        writeExtension(root, "solo", manifest("solo", "1.0.0", GoodExtension.class));

        ExtensionManager manager = new ExtensionManager();
        manager.loadAll(root);

        assertThat(manager.registry().isActive("solo")).isTrue();
        assertThat(manager.registry().snapshot())
                .filteredOn(info -> info.rejectionReason() == RejectionReason.CYCLIC_DEPENDENCY)
                .extracting(info -> info.id())
                .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void dependencyChainLoadsInOrder() throws IOException {
        log.info("用例: 依赖链 base<-mid<-top 全部 ACTIVE，版本区间匹配");
        writeExtension(root, "base", manifest("base", "1.2.0", GoodExtension.class));
        writeExtension(root, "mid",
                manifest("mid", "1.0.0", "1", GoodExtension.class,
                        "[{\"id\":\"base\",\"version\":\"[1.0,2.0)\"}]", null));
        writeExtension(root, "top",
                manifest("top", "1.0.0", "1", GoodExtension.class,
                        "[{\"id\":\"mid\",\"version\":\"1.0.0\"}]", null));

        ExtensionManager manager = new ExtensionManager();
        manager.loadAll(root);

        assertThat(manager.registry().isActive("base")).isTrue();
        assertThat(manager.registry().isActive("mid")).isTrue();
        assertThat(manager.registry().isActive("top")).isTrue();
    }
}
