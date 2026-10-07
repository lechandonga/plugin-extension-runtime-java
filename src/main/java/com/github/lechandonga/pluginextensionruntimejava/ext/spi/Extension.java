package com.github.lechandonga.pluginextensionruntimejava.ext.spi;

/**
 * 第三方扩展需要实现的 SPI。实现类必须提供公开的无参构造方法，
 * 由独立的 {@link java.net.URLClassLoader} 加载，与其它扩展实现类相互隔离。
 */
public interface Extension {

    /**
     * 扩展启动。此时依赖扩展已按拓扑序启动完成，可通过 context 获取依赖与配置。
     */
    default void start(ExtensionContext context) throws Exception {
    }

    /**
     * 扩展停止（热更新切换或整体关闭时调用），用于释放资源。
     */
    default void stop() {
    }

    /**
     * 业务调用入口，参数与返回值均为应用类加载器可见的普通类型。
     */
    default Object invoke(String action, Object payload) {
        return null;
    }
}
