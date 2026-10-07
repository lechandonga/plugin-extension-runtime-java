package com.github.lechandonga.pluginextensionruntimejava.extension;

/**
 * 扩展的只读状态快照，供外部查询加载结果。
 */
public record ExtensionInfo(
        String id,
        String version,
        String source,
        ExtensionState state,
        RejectionReason rejectionReason,
        String detail) {

    public static ExtensionInfo rejected(String id, String source, RejectionReason reason, String detail) {
        return new ExtensionInfo(id, null, source, ExtensionState.REJECTED, reason, detail);
    }
}
