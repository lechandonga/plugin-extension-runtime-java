package com.github.lechandonga.pluginextensionruntimejava.ext.model;

import java.nio.file.Path;

/**
 * 一次扫描中某个扩展来源被拒绝的记录。
 */
public record Rejection(String extensionId, Path source, RejectReason reason, String detail) {
}
