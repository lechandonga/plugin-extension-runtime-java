package com.github.lechandonga.pluginextensionruntimejava.extension;

/**
 * 扩展被拒绝/失败的可区分原因。
 */
public enum RejectionReason {
    /** 清单文件缺失、JSON 非法或必填字段缺失。 */
    INVALID_MANIFEST,
    /** 同一扩展标识被多个来源声明。 */
    DUPLICATE_ID,
    /** 扩展要求的框架 API 版本与当前框架不兼容。 */
    INCOMPATIBLE_API_VERSION,
    /** 声明的依赖扩展不存在或自身被拒绝。 */
    MISSING_DEPENDENCY,
    /** 依赖扩展的实际版本不在声明的版本区间内。 */
    VERSION_CONFLICT,
    /** 依赖关系构成环。 */
    CYCLIC_DEPENDENCY,
    /** 实现类加载、实例化或 start 回调抛错。 */
    INSTANTIATION_FAILURE
}
