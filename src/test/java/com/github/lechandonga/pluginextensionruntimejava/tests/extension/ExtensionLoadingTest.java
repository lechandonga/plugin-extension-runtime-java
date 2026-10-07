package com.github.lechandonga.pluginextensionruntimejava.tests.extension;

import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionInfo;
import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionManager;
import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionState;
import com.github.lechandonga.pluginextensionruntimejava.extension.RejectionReason;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.GoodExtension;
import static com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.ExplodingExtension;
import static com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.manifest;
import static com.github.lechandonga.pluginextensionruntimejava.tests.extension.ExtensionTestSupport.writeExtension;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务场景【扩展发现与生命周期】：清单非法、标识重复、API 版本不兼容、
 * 单扩展失败隔离、配置注入优先级。
 */
class ExtensionLoadingTest {

    private static final Logger log = LoggerFactory.getLogger(ExtensionLoadingTest.class);

    @TempDir
    Path root;

    @BeforeAll
    static void businessScope() {
        log.info("==== 业务覆盖: 扩展发现与生命周期管理 (加载结果查询/拒绝原因区分/失败隔离/配置注入) ====");
    }

    @Test
    void invalidManifestIsRejectedWithDistinguishableReason() throws IOException {
        log.info("用例: 清单 JSON 非法 -> REJECTED/INVALID_MANIFEST");
        Path dir = Files.createDirectories(root.resolve("broken"));
        Files.writeString(dir.resolve("extension-manifest.json"), "{not-json");

        ExtensionManager manager = new ExtensionManager();
        var results = manager.loadAll(root);

        assertThat(results).singleElement().satisfies(info -> {
            assertThat(info.state()).isEqualTo(ExtensionState.REJECTED);
            assertThat(info.rejectionReason()).isEqualTo(RejectionReason.INVALID_MANIFEST);
            assertThat(info.source()).contains("broken");
        });
    }

    @Test
    void duplicateIdIsRejectedAndFirstOneSurvives() throws IOException {
        log.info("用例: 扩展标识重复 -> 后者 REJECTED/DUPLICATE_ID，前者正常 ACTIVE");
        writeExtension(root, "a", manifest("dup", "1.0.0", GoodExtension.class));
        writeExtension(root, "b", manifest("dup", "2.0.0", GoodExtension.class));

        ExtensionManager manager = new ExtensionManager();
        manager.loadAll(root);

        assertThat(manager.registry().get("dup")).isPresent()
                .get().satisfies(h -> assertThat(h.state()).isEqualTo(ExtensionState.ACTIVE));
        assertThat(manager.registry().snapshot())
                .anySatisfy(info -> {
                    assertThat(info.state()).isEqualTo(ExtensionState.REJECTED);
                    assertThat(info.rejectionReason()).isEqualTo(RejectionReason.DUPLICATE_ID);
                });
    }

    @Test
    void incompatibleApiVersionIsRejected() throws IOException {
        log.info("用例: 接口版本不兼容 -> REJECTED/INCOMPATIBLE_API_VERSION");
        writeExtension(root, "legacy", manifest("legacy", "1.0.0", "0", GoodExtension.class, "[]", null));

        ExtensionManager manager = new ExtensionManager();
        manager.loadAll(root);

        assertThat(manager.registry().snapshot()).singleElement().satisfies(info -> {
            assertThat(info.state()).isEqualTo(ExtensionState.REJECTED);
            assertThat(info.rejectionReason()).isEqualTo(RejectionReason.INCOMPATIBLE_API_VERSION);
        });
    }

    @Test
    void failingExtensionDoesNotAffectOthers() throws IOException {
        log.info("用例: 单扩展 start 抛错 -> 该扩展 REJECTED/INSTANTIATION_FAILURE，其它扩展 ACTIVE");
        writeExtension(root, "bad", manifest("bad", "1.0.0", ExplodingExtension.class));
        writeExtension(root, "good", manifest("good", "1.0.0", GoodExtension.class));

        ExtensionManager manager = new ExtensionManager();
        manager.loadAll(root);

        assertThat(manager.registry().isActive("good")).isTrue();
        assertThat(manager.registry().snapshot())
                .anySatisfy(info -> {
                    assertThat(info.id()).isEqualTo("bad");
                    assertThat(info.state()).isEqualTo(ExtensionState.REJECTED);
                    assertThat(info.rejectionReason()).isEqualTo(RejectionReason.INSTANTIATION_FAILURE);
                });
    }

    @Test
    void configOverridePriorityIsDeterministic() throws IOException {
        log.info("用例: 配置优先级 外部覆盖 > config.properties > 清单默认");
        GoodExtension.LAST_CONFIG.clear();
        writeExtension(root, "cfg",
                manifest("cfg", "1.0.0", "1", GoodExtension.class, "[]",
                        Map.of("key", "manifest-default", "only-default", "d")),
                Map.of("key", "file-value"));

        ExtensionManager manager = new ExtensionManager(Map.of("cfg.key", "external-override"));
        manager.loadAll(root);

        assertThat(GoodExtension.LAST_CONFIG)
                .containsEntry("key", "external-override")
                .containsEntry("only-default", "d");
    }
}
