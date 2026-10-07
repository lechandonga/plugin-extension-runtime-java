package com.github.lechandonga.pluginextensionruntimejava.ext.runtime;

/**
 * 扩展运行期 invoke 抛出的包装异常。错误被限制在单个扩展内：
 * 异常只传播给本次调用方并记录到该扩展状态，其它扩展与服务整体继续可用。
 */
public class ExtensionInvocationException extends RuntimeException {

    public ExtensionInvocationException(String extensionId, Throwable cause) {
        super("扩展 '" + extensionId + "' 执行失败: " + cause, cause);
    }
}
