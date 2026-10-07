package com.github.lechandonga.pluginextensionruntimejava.ext.runtime;

import com.github.lechandonga.pluginextensionruntimejava.ext.model.RejectReason;

import com.github.lechandonga.pluginextensionruntimejava.ext.assembly.DependencyResolution;

import java.util.Map;

/**
 * 候选注册表装配中止异常：新扫描结果未能完整启动，调用方必须整体回退到旧注册表。
 */
class ReloadAbortedException extends RuntimeException {

    private final RejectReason reason;
    private Map<String, DependencyResolution.RejectedExtension> rejected = Map.of();

    ReloadAbortedException(RejectReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    RejectReason reason() {
        return reason;
    }

    Map<String, DependencyResolution.RejectedExtension> rejected() {
        return rejected;
    }

    void attachRejections(Map<String, DependencyResolution.RejectedExtension> rejections) {
        this.rejected = Map.copyOf(rejections);
    }
}
