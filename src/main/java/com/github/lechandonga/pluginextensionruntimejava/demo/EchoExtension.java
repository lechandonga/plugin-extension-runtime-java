package com.github.lechandonga.pluginextensionruntimejava.demo;

import com.github.lechandonga.pluginextensionruntimejava.extension.Extension;
import com.github.lechandonga.pluginextensionruntimejava.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例扩展：读取命名空间配置并打印问候语。
 */
public class EchoExtension implements Extension {

    private static final Logger log = LoggerFactory.getLogger(EchoExtension.class);

    @Override
    public void start(ExtensionContext context) {
        log.info("EchoExtension 启动, greeting={}", context.getConfig("greeting", "hello"));
    }
}
