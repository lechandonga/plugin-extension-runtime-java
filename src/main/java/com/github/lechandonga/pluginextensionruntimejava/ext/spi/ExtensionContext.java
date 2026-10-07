package com.github.lechandonga.pluginextensionruntimejava.ext.spi;

/**
 * 启动期与运行期注入给扩展的上下文：独立命名空间配置 + 依赖扩展访问。
 */
public interface ExtensionContext {

    /** 当前扩展自身的 id。 */
    String selfId();

    /**
     * 读取本扩展命名空间内的配置值。key 不带命名空间前缀，
     * 例如当前扩展 id 为 demo，则 getConfig("greeting") 实际对应 ext.demo.greeting。
     */
    String getConfig(String key);

    /** 同 getConfig(key)，缺省时返回默认值。 */
    String getConfig(String key, String defaultValue);

    /** 按依赖 id 获取已启动的依赖扩展；不存在时抛出 IllegalArgumentException。 */
    Extension requireDependency(String dependencyId);
}
