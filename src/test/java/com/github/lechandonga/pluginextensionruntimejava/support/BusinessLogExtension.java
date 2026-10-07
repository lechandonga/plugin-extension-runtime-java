package com.github.lechandonga.pluginextensionruntimejava.support;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 让单测日志清晰展示“业务模块 -> 用例 -> 通过/失败”的覆盖情况。
 */
public class BusinessLogExtension implements BeforeAllCallback, AfterAllCallback,
        BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final String START_KEY = "caseStart";

    @Override
    public void beforeAll(ExtensionContext context) {
        logger(context).info("========== 业务模块【{}】测试开始 ==========", businessName(context));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        logger(context).info("========== 业务模块【{}】测试结束 ==========", businessName(context));
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        context.getStore(ExtensionContext.Namespace.GLOBAL).put(START_KEY, System.nanoTime());
        logger(context).info("---- [用例开始] {} ----", context.getDisplayName());
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        Long start = context.getStore(ExtensionContext.Namespace.GLOBAL).get(START_KEY, Long.class);
        long costMs = start == null ? -1 : (System.nanoTime() - start) / 1_000_000;
        boolean failed = context.getExecutionException().isPresent();
        logger(context).info("---- [用例{}] {}（耗时 {} ms） ----",
                failed ? "失败" : "通过", context.getDisplayName(), costMs);
    }

    private String businessName(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        BusinessModule annotation = testClass.getAnnotation(BusinessModule.class);
        return annotation != null ? annotation.value() : testClass.getSimpleName();
    }

    private Logger logger(ExtensionContext context) {
        return LoggerFactory.getLogger(context.getRequiredTestClass());
    }
}
