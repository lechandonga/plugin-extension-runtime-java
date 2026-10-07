package com.github.lechandonga.pluginextensionruntimejava.extension;

import com.github.lechandonga.pluginextensionruntimejava.extension.config.ExtensionConfig;
import com.github.lechandonga.pluginextensionruntimejava.extension.config.ExtensionConfigResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 扩展生命周期管理器：发现、校验、依赖装配、热更新与故障隔离。
 * 单个扩展失败只记录为 REJECTED/FAILED，绝不影响其它扩展。
 */
public class ExtensionManager {

    /** 当前框架 API 主版本，扩展清单 apiVersion 须一致。 */
    public static final String CURRENT_API_VERSION = "1";

    private static final Logger log = LoggerFactory.getLogger(ExtensionManager.class);

    private final ExtensionRegistry registry = new ExtensionRegistry();
    private final ManifestParser manifestParser = new ManifestParser();
    private final ExtensionConfigResolver configResolver = new ExtensionConfigResolver();
    private final Map<String, String> externalOverrides;
    /** 热更新串行化锁：保证同一时刻只有一次装配/替换，读者经由 AtomicReference 无锁读取。 */
    private final ReentrantLock updateLock = new ReentrantLock();

    public ExtensionManager() {
        this(Map.of());
    }

    public ExtensionManager(Map<String, String> externalOverrides) {
        this.externalOverrides = externalOverrides == null ? Map.of() : Map.copyOf(externalOverrides);
    }

    public ExtensionRegistry registry() {
        return registry;
    }

    /**
     * 扫描根目录，每个一级子目录视为一个扩展来源，逐个加载，互不影响。
     */
    public List<ExtensionInfo> loadAll(Path extensionsRoot) {
        Map<String, Path> dirsById = new LinkedHashMap<>();
        Map<String, ExtensionManifest> manifests = new LinkedHashMap<>();

        try (Stream<Path> stream = Files.list(extensionsRoot)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                try {
                    ExtensionManifest manifest = manifestParser.parse(dir);
                    if (manifests.containsKey(manifest.id())) {
                        registry.recordRejection(manifest.id() + "@" + dir,
                                ExtensionInfo.rejected(manifest.id(), dir.toString(),
                                        RejectionReason.DUPLICATE_ID,
                                        "扩展标识重复，与 " + dirsById.get(manifest.id()) + " 冲突"));
                        continue;
                    }
                    manifests.put(manifest.id(), manifest);
                    dirsById.put(manifest.id(), dir);
                } catch (ExtensionLoadException e) {
                    registry.recordRejection(ExtensionInfo.rejected(dir.getFileName().toString(),
                            dir.toString(), e.reason(), e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("扫描扩展目录失败: " + extensionsRoot, e);
        }

        for (String id : resolveLoadOrder(manifests)) {
            ExtensionManifest manifest = manifests.get(id);
            if (manifest == null) {
                continue; // 已因依赖问题被拒绝
            }
            try {
                validateAgainstRegistry(manifest, manifests, Set.of());
                assembleAndInstall(manifest, dirsById.get(id));
            } catch (ExtensionLoadException e) {
                manifests.remove(id);
                registry.recordRejection(ExtensionInfo.rejected(id, dirsById.get(id).toString(),
                        e.reason(), e.getMessage()));
            }
        }
        return List.copyOf(registry.snapshot());
    }

    /**
     * 热更新单个扩展：先在锁内完整构建并启动新版本，再原子替换；
     * 任何一步失败都保留旧版本继续服务。并发读者只会看到旧版本或新版本。
     */
    public ExtensionInfo update(Path extensionDir) {
        updateLock.lock();
        try {
            ExtensionManifest manifest = manifestParser.parse(extensionDir);
            String id = manifest.id();
            var previous = registry.get(id);

            try {
                validateAgainstRegistry(manifest, Map.of(), Set.of(id));
                ExtensionRegistry.ExtensionHolder fresh = build(manifest, extensionDir);
                ExtensionRegistry.ExtensionHolder old = registry.install(fresh);
                stopQuietly(old);
                log.info("扩展 [{}] 热更新成功: {} -> {}", id,
                        old == null ? "(none)" : old.manifest().version(), manifest.version());
                return fresh.toInfo();
            } catch (ExtensionLoadException e) {
                log.warn("扩展 [{}] 热更新失败，回退保留旧版本 {}: {}", id,
                        previous.map(p -> p.manifest().version()).orElse("(none)"), e.getMessage());
                return previous.map(ExtensionRegistry.ExtensionHolder::toInfo)
                        .orElseGet(() -> {
                            ExtensionInfo rejected = ExtensionInfo.rejected(id, extensionDir.toString(),
                                    e.reason(), e.getMessage());
                            registry.recordRejection(rejected);
                            return rejected;
                        });
            }
        } catch (ExtensionLoadException e) {
            ExtensionInfo rejected = ExtensionInfo.rejected(extensionDir.getFileName().toString(),
                    extensionDir.toString(), e.reason(), e.getMessage());
            registry.recordRejection(rejected);
            return rejected;
        } finally {
            updateLock.unlock();
        }
    }

    /**
     * 故障隔离地调用扩展：扩展抛错只记录并返回 null，不扩散到调用方与其它扩展。
     */
    public <T> T invoke(String id, Function<Extension, T> action) {
        var holder = registry.get(id)
                .filter(h -> h.state() == ExtensionState.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("扩展不可用: " + id));
        try {
            return action.apply(holder.extension());
        } catch (RuntimeException e) {
            log.error("扩展 [{}] 运行期抛错，已隔离: {}", id, e.toString());
            return null;
        }
    }

    private void assembleAndInstall(ExtensionManifest manifest, Path dir) throws ExtensionLoadException {
        updateLock.lock();
        try {
            ExtensionRegistry.ExtensionHolder holder = build(manifest, dir);
            stopQuietly(registry.install(holder));
            log.info("扩展 [{}] v{} 装配完成，来源: {}", manifest.id(), manifest.version(), dir);
        } finally {
            updateLock.unlock();
        }
    }

    /** 完整构建新版本（实例化 + 配置注入 + start），任何失败都在安装前抛出。 */
    private ExtensionRegistry.ExtensionHolder build(ExtensionManifest manifest, Path dir)
            throws ExtensionLoadException {
        if (!CURRENT_API_VERSION.equals(manifest.apiVersion())) {
            throw new ExtensionLoadException(RejectionReason.INCOMPATIBLE_API_VERSION,
                    "扩展 [" + manifest.id() + "] 要求 API 版本 " + manifest.apiVersion()
                            + "，当前框架为 " + CURRENT_API_VERSION);
        }
        Extension extension;
        try {
            Class<?> type = Class.forName(manifest.implementation());
            extension = (Extension) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new ExtensionLoadException(RejectionReason.INSTANTIATION_FAILURE,
                    "实现类加载失败: " + manifest.implementation(), e);
        }
        ExtensionConfig config = configResolver.resolve(manifest.id(), manifest.safeConfig(), dir, externalOverrides);
        ExtensionContext context = new ExtensionContext(manifest.id(), manifest.version(), config);
        try {
            extension.start(context);
        } catch (Exception e) {
            throw new ExtensionLoadException(RejectionReason.INSTANTIATION_FAILURE,
                    "扩展 [" + manifest.id() + "] start 回调失败", e);
        }
        return new ExtensionRegistry.ExtensionHolder(manifest, extension, context,
                ExtensionState.ACTIVE, dir == null ? "(unknown)" : dir.toString());
    }

    /**
     * 校验依赖：缺失依赖、版本区间冲突、循环依赖一律明确拒绝。
     *
     * @param pending  同批次待装配清单（loadAll 场景），为空时只参照注册表
     * @param selfId   热更新时自身的 id（允许替换自己，不构成环）
     */
    private void validateAgainstRegistry(ExtensionManifest manifest,
                                         Map<String, ExtensionManifest> pending,
                                         Set<String> selfId) throws ExtensionLoadException {
        for (ExtensionManifest.DependencyDecl dep : manifest.safeDependencies()) {
            if (selfId.contains(dep.id())) {
                continue;
            }
            VersionRange.Version actual = null;
            var active = registry.get(dep.id());
            if (active.isPresent() && active.get().state() == ExtensionState.ACTIVE) {
                actual = VersionRange.Version.parse(active.get().manifest().version());
            } else if (pending.containsKey(dep.id())) {
                actual = VersionRange.Version.parse(pending.get(dep.id()).version());
            }
            if (actual == null) {
                throw new ExtensionLoadException(RejectionReason.MISSING_DEPENDENCY,
                        "扩展 [" + manifest.id() + "] 缺失依赖: " + dep.id());
            }
            if (dep.version() != null && !VersionRange.parse(dep.version()).contains(actual)) {
                throw new ExtensionLoadException(RejectionReason.VERSION_CONFLICT,
                        "扩展 [" + manifest.id() + "] 依赖 " + dep.id() + " 版本区间 "
                                + dep.version() + " 与实际版本 " + actual + " 冲突");
            }
        }
    }

    /** 拓扑排序得到装配顺序；循环依赖与缺失依赖在排序阶段即明确拒绝。 */
    private List<String> resolveLoadOrder(Map<String, ExtensionManifest> manifests) {
        List<String> order = new ArrayList<>();
        Set<String> done = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        Map<String, ExtensionManifest> remaining = new HashMap<>(manifests);

        for (String id : List.copyOf(remaining.keySet())) {
            visit(id, remaining, done, visiting, stack, order);
        }
        return order;
    }

    private void visit(String id, Map<String, ExtensionManifest> manifests, Set<String> done,
                       Set<String> visiting, Deque<String> stack, List<String> order) {
        if (done.contains(id) || !manifests.containsKey(id)) {
            return;
        }
        if (!visiting.add(id)) {
            List<String> cycle = new ArrayList<>(stack);
            cycle.add(id);
            for (String member : new HashSet<>(cycle)) {
                registry.recordRejection(ExtensionInfo.rejected(member, "(graph)",
                        RejectionReason.CYCLIC_DEPENDENCY, "检测到循环依赖: " + cycle));
                manifests.remove(member);
            }
            return;
        }
        stack.push(id);
        ExtensionManifest manifest = manifests.get(id);
        boolean broken = false;
        for (ExtensionManifest.DependencyDecl dep : manifest.safeDependencies()) {
            if (dep.id().equals(id)) {
                continue;
            }
            if (manifests.containsKey(dep.id())) {
                visit(dep.id(), manifests, done, visiting, stack, order);
            }
            if (!manifests.containsKey(dep.id()) && !registry.isActive(dep.id())) {
                if (done.contains(dep.id()) || registry.isActive(dep.id())) {
                    continue;
                }
                registry.recordRejection(ExtensionInfo.rejected(id, "(graph)",
                        RejectionReason.MISSING_DEPENDENCY, "扩展 [" + id + "] 缺失依赖: " + dep.id()));
                manifests.remove(id);
                broken = true;
                break;
            }
        }
        stack.pop();
        visiting.remove(id);
        if (!broken && manifests.containsKey(id)) {
            done.add(id);
            order.add(id);
        }
    }

    private void stopQuietly(ExtensionRegistry.ExtensionHolder holder) {
        if (holder == null) {
            return;
        }
        try {
            holder.extension().stop();
        } catch (RuntimeException e) {
            log.warn("扩展 [{}] stop 回调抛错，已忽略: {}", holder.manifest().id(), e.toString());
        }
    }
}
