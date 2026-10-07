package com.github.lechandonga.pluginextensionruntimejava.ext.runtime;

import com.github.lechandonga.pluginextensionruntimejava.ext.assembly.DependencyResolution;
import com.github.lechandonga.pluginextensionruntimejava.ext.assembly.DependencyResolution.RejectedExtension;
import com.github.lechandonga.pluginextensionruntimejava.ext.assembly.DependencyResolver;
import com.github.lechandonga.pluginextensionruntimejava.ext.config.ConfigResolution;
import com.github.lechandonga.pluginextensionruntimejava.ext.config.ConfigResolver;
import com.github.lechandonga.pluginextensionruntimejava.ext.discovery.ExtensionClassLoader;
import com.github.lechandonga.pluginextensionruntimejava.ext.discovery.ExtensionScanner;
import com.github.lechandonga.pluginextensionruntimejava.ext.discovery.ExtensionScanner.ScannedExtension;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.ExtensionStateView;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.ExtensionStatus;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.RejectReason;
import com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 扩展运行时：发现 → 装配 → 实例化 → 按拓扑序启动 → 不可变注册表整体发布。
 *
 * 热更新语义：
 * <ul>
 *   <li>{@link #reload()} 与所有读操作互斥，读操作之间完全并发；</li>
 *   <li>新注册表只有在全部目标扩展成功启动后才一次性发布，
 *       并发调用只能观察到旧版本或新版本，不存在半加载状态；</li>
 *   <li>扫描、装配或任一扩展启动失败时，所有新版本尝试全部丢弃并停止，
 *       旧注册表原封不动继续服务，即失败回退到上一版本。</li>
 * </ul>
 */
public class ExtensionRuntime {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRuntime.class);

    private final Path extensionsRoot;
    private final ExtensionScanner scanner;
    private final DependencyResolver dependencyResolver;
    private final ConfigResolver configResolver;

    private final AtomicReference<ExtensionRegistry> registryRef = new AtomicReference<>();
    private final Object reloadLock = new Object();
    private volatile String lastUpdateError;

    public ExtensionRuntime(Path extensionsRoot, String supportedApiVersion, Path overrideFile) {
        this.extensionsRoot = extensionsRoot;
        this.scanner = new ExtensionScanner();
        this.dependencyResolver = new DependencyResolver(supportedApiVersion);
        this.configResolver = new ConfigResolver(overrideFile);
    }

    /** 首次加载：清单/装配/启动失败只体现在状态查询中，不抛出异常，不影响其它扩展。 */
    public void start() {
        synchronized (reloadLock) {
            registryRef.set(assemble());
            log.info("扩展运行时启动完成：活跃扩展 {}，被拒绝扩展 {}",
                    registryRef.get().active().size(), registryRef.get().rejected().size());
        }
    }

    /**
     * 热更新。返回确定性结果；失败时整体回退到更新前的注册表并保留全部旧版本继续服务。
     */
    public ReloadResult reload() {
        synchronized (reloadLock) {
            ExtensionRegistry previous = registryRef.get();
            Map<String, String> previousVersions = new HashMap<>();
            if (previous != null) {
                previous.active().forEach((id, ext) ->
                        previousVersions.put(id, ext.scanned().manifest().version().toString()));
            }
            ExtensionRegistry candidate = assemble();

            List<String> broken = new ArrayList<>();
            for (RejectedExtension rejection : candidate.rejected().values()) {
                String id = rejection.id();
                // 只关心“相对上一版本变坏”的扩展：此前 ACTIVE 的现在被拒，或本次新出现目录的扩展被拒
                boolean wasActive = previous.active().containsKey(id);
                boolean wasRejected = previous.rejected().values().stream()
                        .anyMatch(r -> id.equals(r.id()));
                if (!wasRejected) {
                    broken.add(id + "(" + rejection.reason() + (wasActive ? ",旧版本在服务" : ",新增") + ")");
                }
            }
            for (String id : previous.active().keySet()) {
                if (!candidate.active().containsKey(id)
                        && candidate.rejected().values().stream().noneMatch(r -> id.equals(r.id()))) {
                    broken.add(id + "(从扩展目录中消失)");
                }
            }
            if (!broken.isEmpty()) {
                String message = "新装配结果会使在服务扩展失效: " + String.join(", ", broken)
                        + "；已整体回退到上一版本";
                shutdownQuietly(candidate);
                lastUpdateError = message;
                log.warn("热更新失败，保持旧版本继续服务：{}", message);
                return ReloadResult.rollback(message);
            }

            Map<String, String> newVersions = new HashMap<>();
            candidate.active().forEach((id, ext) ->
                    newVersions.put(id, ext.scanned().manifest().version().toString()));

            List<String> updated = new ArrayList<>();
            List<String> unchanged = new ArrayList<>();
            for (Map.Entry<String, String> entry : newVersions.entrySet()) {
                String old = previousVersions.get(entry.getKey());
                if (old == null || !old.equals(entry.getValue())) {
                    updated.add(old == null
                            ? entry.getKey() + "(新增)"
                            : entry.getKey() + "(" + old + " -> " + entry.getValue() + ")");
                } else {
                    unchanged.add(entry.getKey());
                }
            }
            List<String> removed = new ArrayList<>();
            for (String id : previousVersions.keySet()) {
                if (!newVersions.containsKey(id)) {
                    removed.add(id);
                }
            }

            registryRef.set(candidate);
            lastUpdateError = null;
            shutdownQuietly(previous);
            log.info("热更新成功：更新 {}，保持 {}，移除 {}", updated, unchanged, removed);
            return ReloadResult.success(updated, unchanged, removed);
        }
    }

    public Optional<Extension> getExtension(String id) {
        ExtensionRegistry registry = registryRef.get();
        if (registry == null) {
            return Optional.empty();
        }
        ActiveExtension ext = registry.active().get(id);
        return ext == null ? Optional.empty() : Optional.of(ext.instance());
    }

    public boolean exists(String id) {
        ExtensionRegistry registry = registryRef.get();
        return registry != null && registry.active().containsKey(id);
    }

    /**
     * 调用扩展。扩展自身抛出的任何错误被包装后只抛给本次调用方，
     * 并记录在该扩展的 lastRuntimeError 上，不会拖垮其它扩展或服务整体。
     */
    public Object invoke(String id, String action, Object payload) {
        ExtensionRegistry registry = registryRef.get();
        if (registry == null) {
            throw new ExtensionInvocationException(id, new IllegalStateException("扩展运行时尚未启动"));
        }
        ActiveExtension ext = registry.active().get(id);
        if (ext == null) {
            throw new ExtensionInvocationException(id,
                    new IllegalArgumentException("扩展不存在或未处于 ACTIVE 状态: " + id));
        }
        try {
            return ext.instance().invoke(action, payload);
        } catch (Throwable throwable) {
            String message = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            ext.recordRuntimeError(Instant.now() + " " + message);
            log.warn("扩展 '{}' 运行期出错（已隔离，不影响其它扩展）: {}", id, message);
            throw new ExtensionInvocationException(id, throwable);
        }
    }

    public List<ExtensionStateView> states() {
        ExtensionRegistry registry = registryRef.get();
        Map<String, ExtensionStateView> views = new LinkedHashMap<>();
        if (registry != null) {
            for (Map.Entry<String, ActiveExtension> entry : registry.active().entrySet()) {
                ActiveExtension ext = entry.getValue();
                views.put(entry.getKey(), new ExtensionStateView(
                        entry.getKey(),
                        ext.scanned().manifest().name(),
                        ExtensionStatus.ACTIVE,
                        ext.scanned().manifest().version().toString(),
                        ext.scanned().manifest().apiVersion(),
                        ext.scanned().directory(),
                        null,
                        null,
                        ext.loadedAt(),
                        ext.lastRuntimeError(),
                        lastUpdateError));
            }
            for (Map.Entry<String, RejectedExtension> entry : registry.rejected().entrySet()) {
                RejectedExtension rejection = entry.getValue();
                String id = rejection.id() != null ? rejection.id() : entry.getKey();
                String name = rejection.manifest() != null ? rejection.manifest().name() : null;
                String version = rejection.manifest() != null ? rejection.manifest().version().toString() : null;
                String apiVersion = rejection.manifest() != null ? rejection.manifest().apiVersion() : null;
                views.put(entry.getKey(), new ExtensionStateView(
                        id, name, ExtensionStatus.REJECTED, version, apiVersion,
                        rejection.source(), rejection.reason(), rejection.detail(),
                        null, null, lastUpdateError));
            }
        }
        return new ArrayList<>(views.values());
    }

    public ConfigResolution configOf(String extensionId) {
        ExtensionRegistry registry = registryRef.get();
        if (registry == null) {
            throw new IllegalArgumentException("扩展运行时尚未启动");
        }
        ActiveExtension ext = registry.active().get(extensionId);
        if (ext == null) {
            throw new IllegalArgumentException("扩展不存在或未处于 ACTIVE 状态: " + extensionId);
        }
        return ext.config();
    }

    public String lastUpdateError() {
        return lastUpdateError;
    }

    @PreDestroy
    public void shutdown() {
        synchronized (reloadLock) {
            shutdownQuietly(registryRef.get());
            registryRef.set(null);
            log.info("扩展运行时已关闭");
        }
    }

    /**
     * 完成一次全新的扫描、装配与启动。
     * 清单/依赖问题来自 DependencyResolver 的 rejected；实现类加载或 start 失败的扩展会被拒绝，
     * 其所有（传递）依赖方一并拒绝，然后重新从头装配剩余扩展——
     * 因此单个扩展加载失败绝不影响其它扩展，也没有任何扩展带着半截状态发布。
     * 该方法不抛出异常；只有“一个候选都无法装配”这种情形在首次启动后不会发生回退问题。
     */
    private ExtensionRegistry assemble() {
        ExtensionScanner.ScanResult scan = scanner.scan(extensionsRoot);
        Map<String, RejectedExtension> extraRejections = new LinkedHashMap<>();

        while (true) {
            Map<String, ScannedExtension> candidates = new LinkedHashMap<>(scan.valid());
            extraRejections.values().forEach(r -> candidates.remove(r.manifest().id()));
            DependencyResolution resolution = dependencyResolver.resolve(candidates, scan.failures());

            Map<String, ActiveExtension> active = new LinkedHashMap<>();
            List<ScannedExtension> startedScans = new ArrayList<>();
            Map<String, ActiveExtension> dependencyIndex = new HashMap<>();
            ScannedExtension failed = null;
            RejectReason failReason = null;
            String failDetail = null;

            for (ScannedExtension scanned : resolution.startupOrder()) {
                Extension instance;
                try {
                    instance = instantiate(scanned);
                } catch (ReloadAbortedException ex) {
                    failed = scanned;
                    failReason = ex.reason();
                    failDetail = ex.getMessage();
                    break;
                }
                ConfigResolution config;
                try {
                    config = configResolver.resolve(scanned.manifest().id(),
                            scanned.manifest().defaultConfig());
                } catch (IOException ex) {
                    failed = scanned;
                    failReason = RejectReason.START_FAILED;
                    failDetail = "扩展 '" + scanned.manifest().id() + "' 配置解析失败: " + ex.getMessage();
                    break;
                }
                DefaultExtensionContext context =
                        new DefaultExtensionContext(scanned.manifest().id(), config.values(), dependencyIndex);
                try {
                    instance.start(context);
                } catch (Throwable throwable) {
                    failed = scanned;
                    failReason = RejectReason.START_FAILED;
                    failDetail = "扩展 '" + scanned.manifest().id() + "' start 抛出异常: "
                            + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
                    break;
                }
                ActiveExtension activeExtension =
                        new ActiveExtension(scanned, instance, config, Instant.now());
                dependencyIndex.put(scanned.manifest().id(), activeExtension);
                active.put(scanned.manifest().id(), activeExtension);
                startedScans.add(scanned);
            }

            if (failed == null) {
                Map<String, RejectedExtension> allRejected =
                        new LinkedHashMap<>(resolution.rejected());
                allRejected.putAll(extraRejections);
                List<String> order = resolution.startupOrder().stream()
                        .map(s -> s.manifest().id()).toList();
                return new ExtensionRegistry(active, order, allRejected, Instant.now());
            }

            // 本轮有扩展失败：停止本轮已启动的全部候选，拒绝失败扩展并连带拒绝其依赖方，再来一轮
            for (int i = startedScans.size() - 1; i >= 0; i--) {
                ActiveExtension partial = active.get(startedScans.get(i).manifest().id());
                if (partial != null) {
                    stopQuietly(partial.instance(), partial.scanned().manifest().id());
                }
            }
            rejectWithDependents(scan.valid(), extraRejections, failed, failReason, failDetail);
            log.warn("扩展装配中拒绝扩展 '{}' [{}]: {}，将重新装配剩余扩展",
                    failed.manifest().id(), failReason, failDetail);
        }
    }

    /** 拒绝 failed 扩展，并把（传递）依赖它的所有扩展一并标记为 MISSING_DEPENDENCY。 */
    private void rejectWithDependents(Map<String, ScannedExtension> scanned,
                                      Map<String, RejectedExtension> extraRejections,
                                      ScannedExtension failed, RejectReason reason, String detail) {
        String failedId = failed.manifest().id();
        if (!extraRejections.containsKey(failedId)) {
            extraRejections.put(failedId,
                    new RejectedExtension(failedId, failed.manifest(), failed.directory(), reason, detail));
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ScannedExtension ext : scanned.values()) {
                if (extraRejections.containsKey(ext.manifest().id())) {
                    continue;
                }
                for (String depId : ext.manifest().dependencies().keySet()) {
                    if (extraRejections.containsKey(depId)) {
                        extraRejections.put(ext.manifest().id(), new RejectedExtension(
                                ext.manifest().id(), ext.manifest(), ext.directory(), RejectReason.MISSING_DEPENDENCY,
                                "依赖 '" + depId + "' 加载/启动失败（"
                                        + extraRejections.get(depId).reason() + "）"));
                        changed = true;
                        break;
                    }
                }
            }
        }
    }

    private Extension instantiate(ScannedExtension scanned) {
        try {
            Class<?> raw = Class.forName(scanned.manifest().className(), true, scanned.classLoader());
            if (!Extension.class.isAssignableFrom(raw)) {
                throw new ReloadAbortedException(RejectReason.CLASS_LOAD_FAILED,
                        "扩展 '" + scanned.manifest().id() + "' 的实现类 "
                                + scanned.manifest().className() + " 未实现 Extension SPI");
            }
            Class<? extends Extension> type = raw.asSubclass(Extension.class);
            return type.getDeclaredConstructor().newInstance();
        } catch (ReloadAbortedException ex) {
            throw ex;
        } catch (Throwable throwable) {
            throw new ReloadAbortedException(RejectReason.CLASS_LOAD_FAILED,
                    "扩展 '" + scanned.manifest().id() + "' 的实现类 "
                            + scanned.manifest().className() + " 加载或实例化失败: "
                            + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    private void shutdownQuietly(ExtensionRegistry registry) {
        if (registry == null) {
            return;
        }
        List<String> order = new ArrayList<>(registry.startupOrder());
        Collections.reverse(order);
        for (String id : order) {
            ActiveExtension ext = registry.active().get(id);
            if (ext != null) {
                stopQuietly(ext.instance(), id);
                try {
                    ext.scanned().close();
                } catch (Exception ignore) {
                    // 关闭失败不影响后续扩展停止
                }
            }
        }
    }

    private void stopQuietly(Extension instance, String id) {
        try {
            instance.stop();
        } catch (Throwable throwable) {
            log.warn("扩展 '{}' stop 抛出异常（已忽略）: {}", id, throwable.getMessage());
        }
    }
}
