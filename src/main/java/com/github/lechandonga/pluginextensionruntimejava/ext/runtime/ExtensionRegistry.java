package com.github.lechandonga.pluginextensionruntimejava.ext.runtime;

import java.time.Instant;

import com.github.lechandonga.pluginextensionruntimejava.ext.assembly.DependencyResolution.RejectedExtension;

import java.util.List;
import java.util.Map;

/**
 * 不可变注册表快照。装配完成后一次性整体替换，
 * 读操作通过 volatile 引用获取，并发调用只能看到旧版本或新版本，不存在半加载状态。
 */
final class ExtensionRegistry {

    private final Map<String, ActiveExtension> active;
    private final List<String> startupOrder;
    private final Map<String, RejectedExtension> rejected;
    private final Instant assembledAt;

    ExtensionRegistry(Map<String, ActiveExtension> active, List<String> startupOrder,
                      Map<String, RejectedExtension> rejected, Instant assembledAt) {
        this.active = Map.copyOf(active);
        this.startupOrder = List.copyOf(startupOrder);
        this.rejected = Map.copyOf(rejected);
        this.assembledAt = assembledAt;
    }

    Map<String, ActiveExtension> active() {
        return active;
    }

    List<String> startupOrder() {
        return startupOrder;
    }

    Map<String, RejectedExtension> rejected() {
        return rejected;
    }

    Instant assembledAt() {
        return assembledAt;
    }
}
