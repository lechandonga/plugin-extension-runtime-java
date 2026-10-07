package com.github.lechandonga.pluginextensionruntimejava.ext.discovery;

import com.github.lechandonga.pluginextensionruntimejava.ext.model.RejectReason;

/**
 * 清单解析失败，携带可区分的拒绝原因。
 */
public class ManifestParseException extends Exception {

    private final RejectReason reason;
    private final String declaredId;

    public ManifestParseException(String message) {
        super(message);
        this.reason = RejectReason.INVALID_MANIFEST;
        this.declaredId = null;
    }

    public ManifestParseException(RejectReason reason, String message) {
        super(message);
        this.reason = reason;
        this.declaredId = null;
    }

    public ManifestParseException(RejectReason reason, String declaredId, String message) {
        super(message);
        this.reason = reason;
        this.declaredId = declaredId;
    }

    public RejectReason reason() {
        return reason;
    }

    public String declaredId() {
        return declaredId;
    }
}
