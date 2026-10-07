package com.github.lechandonga.pluginextensionruntimejava.ext.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 扩展运行时配置：
 * extensions.root     本地扩展根目录（每个子目录是一个扩展）
 * extensions.api-version 运行时支持的接口版本
 * extensions.override-file 外部覆盖配置文件（properties）
 * extensions.watch    是否监听根目录变化并自动热更新（默认关闭，行为确定）
 */
@ConfigurationProperties(prefix = "extensions")
public class ExtensionProperties {

    private Path root;
    private String apiVersion = "1.0";
    private Path overrideFile;
    private boolean watch = false;

    public Path getRoot() {
        return root;
    }

    public void setRoot(Path root) {
        this.root = root;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public Path getOverrideFile() {
        return overrideFile;
    }

    public void setOverrideFile(Path overrideFile) {
        this.overrideFile = overrideFile;
    }

    public boolean isWatch() {
        return watch;
    }

    public void setWatch(boolean watch) {
        this.watch = watch;
    }
}
