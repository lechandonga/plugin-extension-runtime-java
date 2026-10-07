package com.github.lechandonga.pluginextensionruntimejava.extension;

/**
 * 扩展加载/装配失败，携带可区分的拒绝原因。
 */
public class ExtensionLoadException extends Exception {

    private final RejectionReason reason;

    public ExtensionLoadException(RejectionReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ExtensionLoadException(RejectionReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public RejectionReason reason() {
        return reason;
    }
}
