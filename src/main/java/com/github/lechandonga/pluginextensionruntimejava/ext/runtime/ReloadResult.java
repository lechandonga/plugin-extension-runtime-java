package com.github.lechandonga.pluginextensionruntimejava.ext.runtime;

import java.util.List;

/**
 * 一次热更新的确定性结果说明。
 */
public record ReloadResult(boolean rolledBack, List<String> updatedIds, List<String> unchangedIds,
                           List<String> removedIds, String errorMessage) {

    static ReloadResult success(List<String> updatedIds, List<String> unchangedIds, List<String> removedIds) {
        return new ReloadResult(false, List.copyOf(updatedIds), List.copyOf(unchangedIds),
                List.copyOf(removedIds), null);
    }

    static ReloadResult rollback(String errorMessage) {
        return new ReloadResult(true, List.of(), List.of(), List.of(), errorMessage);
    }
}
