package com.github.lechandonga.pluginextensionruntimejava.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注测试类覆盖的业务模块名称，输出到测试日志中。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@org.junit.jupiter.api.extension.ExtendWith(BusinessLogExtension.class)
public @interface BusinessModule {
    String value();
}
