package com.github.lechandonga.pluginextensionruntimejava.ext.assembly;

import com.github.lechandonga.pluginextensionruntimejava.ext.discovery.ExtensionScanner.ScannedExtension;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.Manifest;

import java.nio.file.Path;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.RejectReason;

import java.util.List;
import java.util.Map;

/**
 * 依赖装配结果：可启动的扩展（按拓扑序排列）+ 被拒绝扩展及其原因。
 */
public record DependencyResolution(List<ScannedExtension> startupOrder,
                                   Map<String, DependencyResolution.RejectedExtension> rejected) {

    public record RejectedExtension(String id, Manifest manifest, Path source,
                                    RejectReason reason, String detail) {
    }
}
