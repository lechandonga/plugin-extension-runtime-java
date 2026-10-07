package com.github.lechandonga.pluginextensionruntimejava.ext.model;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 扩展状态的只读视图，供查询接口返回。
 */
public record ExtensionStateView(
        String id,
        String name,
        ExtensionStatus status,
        String version,
        String apiVersion,
        Path source,
        RejectReason rejectReason,
        String rejectDetail,
        Instant loadedAt,
        String lastRuntimeError,
        String lastUpdateError) {
}
