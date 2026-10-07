package com.github.lechandonga.pluginextensionruntimejava.ext.model;

/**
 * 扩展被拒绝 / 加载失败的可区分原因。
 */
public enum RejectReason {
    INVALID_MANIFEST("清单非法（缺失必填字段、JSON 格式错误或版本号无法解析）"),
    DUPLICATE_ID("扩展标识重复"),
    INCOMPATIBLE_API_VERSION("接口版本不兼容"),
    MISSING_DEPENDENCY("声明的依赖缺失或依赖自身被拒绝"),
    VERSION_RANGE_CONFLICT("依赖版本不在声明区间内"),
    CIRCULAR_DEPENDENCY("依赖图存在循环"),
    CLASS_LOAD_FAILED("扩展实现类加载或实例化失败"),
    START_FAILED("扩展启动（start）抛出异常");

    private final String description;

    RejectReason(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
