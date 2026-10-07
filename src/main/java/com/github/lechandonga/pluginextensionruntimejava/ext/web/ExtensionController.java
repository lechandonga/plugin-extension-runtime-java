package com.github.lechandonga.pluginextensionruntimejava.ext.web;

import com.github.lechandonga.pluginextensionruntimejava.ext.config.ConfigResolution;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.ExtensionStateView;
import com.github.lechandonga.pluginextensionruntimejava.ext.runtime.ExtensionRuntime;
import com.github.lechandonga.pluginextensionruntimejava.ext.runtime.ReloadResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 扩展运行时的查询/运维接口，便于本地验证装配结果、配置优先级与热更新行为。
 */
@RestController
@RequestMapping("/extensions")
public class ExtensionController {

    private final ObjectProvider<ExtensionRuntime> runtimeProvider;

    public ExtensionController(ObjectProvider<ExtensionRuntime> runtimeProvider) {
        this.runtimeProvider = runtimeProvider;
    }

    @GetMapping
    public List<ExtensionStateView> states() {
        return runtime().states();
    }

    @GetMapping("/{id}/config")
    public ConfigResolution config(@PathVariable String id) {
        return runtime().configOf(id);
    }

    @PostMapping("/reload")
    public ReloadResult reload() {
        return runtime().reload();
    }

    @PostMapping("/{id}/invoke")
    public Object invoke(@PathVariable String id,
                         @RequestParam(defaultValue = "invoke") String action,
                         @RequestBody(required = false) Map<String, Object> payload) {
        return runtime().invoke(id, action, payload);
    }

    private ExtensionRuntime runtime() {
        ExtensionRuntime runtime = runtimeProvider.getIfAvailable();
        if (runtime == null) {
            throw new IllegalStateException("扩展运行时未启用：请配置 extensions.root");
        }
        return runtime;
    }
}
