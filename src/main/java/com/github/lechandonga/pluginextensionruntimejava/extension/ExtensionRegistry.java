package com.github.lechandonga.pluginextensionruntimejava.extension;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 并发安全的扩展注册表。每个扩展对应一个 {@link AtomicReference} 持有者，
 * 热更新时整体替换持有者内容，读线程只会看到完整的旧版本或完整的新版本，
 * 绝不暴露半加载状态。
 */
public class ExtensionRegistry {

    /**
     * 扩展的一次具体装配结果（不可变），热更新时作为整体被原子替换。
     */
    public record ExtensionHolder(ExtensionManifest manifest,
                                  Extension extension,
                                  ExtensionContext context,
                                  ExtensionState state,
                                  String source) {
        ExtensionInfo toInfo() {
            return new ExtensionInfo(manifest.id(), manifest.version(), source, state, null, null);
        }
    }

    private final Map<String, AtomicReference<ExtensionHolder>> active = new ConcurrentHashMap<>();
    private final Map<String, ExtensionInfo> rejected = new ConcurrentHashMap<>();

    /** 安装新版本。若已有旧版本，先原子替换再停止旧实例，返回被替换的旧持有者。 */
    public ExtensionHolder install(ExtensionHolder holder) {
        AtomicReference<ExtensionHolder> ref =
                active.computeIfAbsent(holder.manifest().id(), id -> new AtomicReference<>());
        ExtensionHolder previous = ref.getAndSet(holder);
        rejected.remove(holder.manifest().id());
        return previous;
    }

    public void recordRejection(ExtensionInfo info) {
        recordRejection(info.id(), info);
    }

    /**
     * 记录拒绝原因。同一 key 只保留首次记录，避免后续连带失败覆盖掉根因
     * （如循环依赖被后续的缺失依赖覆盖）。重复标识可用 id@source 作为 key 共存。
     */
    public void recordRejection(String key, ExtensionInfo info) {
        rejected.putIfAbsent(key, info);
    }

    public boolean isRejected(String id) {
        return rejected.containsKey(id);
    }

    /** 读取当前可用扩展，只会拿到完整的旧版本或新版本。 */
    public Optional<ExtensionHolder> get(String id) {
        AtomicReference<ExtensionHolder> ref = active.get(id);
        return ref == null ? Optional.empty() : Optional.ofNullable(ref.get());
    }

    public boolean isActive(String id) {
        return get(id).map(h -> h.state() == ExtensionState.ACTIVE).orElse(false);
    }

    /** 全部扩展的状态快照（含被拒绝的扩展）。 */
    public Collection<ExtensionInfo> snapshot() {
        Map<String, ExtensionInfo> all = new ConcurrentHashMap<>();
        active.forEach((id, ref) -> {
            ExtensionHolder holder = ref.get();
            if (holder != null) {
                all.put(id, holder.toInfo());
            }
        });
        all.putAll(rejected);
        return List.copyOf(all.values());
    }
}
