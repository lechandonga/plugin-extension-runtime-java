package com.github.lechandonga.pluginextensionruntimejava.extension;

/**
 * 扩展生命周期状态。
 */
public enum ExtensionState {
    /** 清单解析与校验通过，实例已创建但尚未启动。 */
    LOADED,
    /** start 回调成功，扩展可对外提供服务。 */
    ACTIVE,
    /** 启动或运行期抛错，扩展不可用但已被隔离。 */
    FAILED,
    /** 校验未通过被拒绝，reason 中携带可区分的拒绝原因。 */
    REJECTED,
    /** 热更新后旧版本进入已替换状态。 */
    REPLACED
}
