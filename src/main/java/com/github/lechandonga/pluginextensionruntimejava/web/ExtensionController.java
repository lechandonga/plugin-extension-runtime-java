package com.github.lechandonga.pluginextensionruntimejava.web;

import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionInfo;
import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/**
 * 扩展状态查询端点。
 */
@RestController
@RequestMapping("/extensions")
public class ExtensionController {

    private final ExtensionManager extensionManager;

    public ExtensionController(ExtensionManager extensionManager) {
        this.extensionManager = extensionManager;
    }

    @GetMapping
    public Collection<ExtensionInfo> list() {
        return extensionManager.registry().snapshot();
    }
}
