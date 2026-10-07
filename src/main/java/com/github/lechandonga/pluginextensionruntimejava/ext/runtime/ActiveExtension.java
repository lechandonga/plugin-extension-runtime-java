package com.github.lechandonga.pluginextensionruntimejava.ext.runtime;

import com.github.lechandonga.pluginextensionruntimejava.ext.config.ConfigResolution;
import com.github.lechandonga.pluginextensionruntimejava.ext.discovery.ExtensionScanner.ScannedExtension;
import com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一个已启动扩展的运行时句柄。单个扩展运行期抛出的错误只记录在自身句柄上，
 * 不影响其它扩展；lastRuntimeError 不影响其 ACTIVE 状态与继续服务能力。
 */
final class ActiveExtension {

    private final ScannedExtension scanned;
    private final Extension instance;
    private final ConfigResolution config;
    private final Instant loadedAt;
    private final AtomicReference<String> lastRuntimeError = new AtomicReference<>();

    ActiveExtension(ScannedExtension scanned, Extension instance, ConfigResolution config, Instant loadedAt) {
        this.scanned = scanned;
        this.instance = instance;
        this.config = config;
        this.loadedAt = loadedAt;
    }

    ScannedExtension scanned() {
        return scanned;
    }

    Extension instance() {
        return instance;
    }

    ConfigResolution config() {
        return config;
    }

    Instant loadedAt() {
        return loadedAt;
    }

    String lastRuntimeError() {
        return lastRuntimeError.get();
    }

    void recordRuntimeError(String message) {
        lastRuntimeError.set(message);
    }
}
