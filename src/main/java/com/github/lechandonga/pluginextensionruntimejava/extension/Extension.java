package com.github.lechandonga.pluginextensionruntimejava.extension;

/**
 * 扩展实现需要实现的接口。框架通过清单中的 implementation 类名反射创建实例，
 * 先注入 {@link ExtensionContext}（含独立命名空间配置），再回调 start。
 */
public interface Extension {

    /**
     * 扩展启动回调。若抛出异常，该扩展进入 FAILED 状态，不影响其它扩展。
     */
    void start(ExtensionContext context) throws Exception;

    /**
     * 扩展停止回调（热更新替换旧版本、框架关闭时调用）。实现需保证幂等。
     */
    default void stop() {
    }
}
