package com.github.lechandonga.pluginextensionruntimejava.tests;

/**
 * 测试用“第三方扩展”源码常量，由测试夹具编译进独立扩展目录的 classes/。
 */
final class ExtensionSources {

    private ExtensionSources() {
    }

    static final String ECHO_V1 = """
            package demo;

            import com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension;
            import com.github.lechandonga.pluginextensionruntimejava.ext.spi.ExtensionContext;

            public class EchoExtension implements Extension {
                private String greeting;
                public void start(ExtensionContext ctx) {
                    this.greeting = ctx.getConfig("greeting", "hello-default");
                }
                public Object invoke(String action, Object payload) {
                    return greeting + ":" + action + ":" + payload;
                }
                public String greeting() { return greeting; }
            }
            """;

    static final String ECHO_V2 = ECHO_V1.replace("hello-default", "hello-default-v2");

    static final String UPPER = """
            package demo;

            import com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension;
            import com.github.lechandonga.pluginextensionruntimejava.ext.spi.ExtensionContext;

            public class UpperExtension implements Extension {
                private volatile String echoVersion;
                public void start(ExtensionContext ctx) {
                    Extension echo = ctx.requireDependency("demo-echo");
                    this.echoVersion = (String) echo.invoke("ping", "from-upper");
                }
                public Object invoke(String action, Object payload) {
                    return "upper[" + echoVersion + "]:" + String.valueOf(payload).toUpperCase();
                }
            }
            """;

    static final String FAIL_START = """
            package demo;

            import com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension;

            public class FailStartExtension implements Extension {
                public void start(com.github.lechandonga.pluginextensionruntimejava.ext.spi.ExtensionContext ctx) {
                    throw new IllegalStateException("故意在启动期失败");
                }
            }
            """;

    static final String FAIL_INVOKE = """
            package demo;

            import com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension;

            public class FailInvokeExtension implements Extension {
                public Object invoke(String action, Object payload) {
                    throw new RuntimeException("故意在运行期抛错");
                }
            }
            """;

    static final String SLOW_MARKER = """
            package demo;

            import com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension;

            public class SlowMarkerExtension implements Extension {
                public void start(com.github.lechandonga.pluginextensionruntimejava.ext.spi.ExtensionContext ctx) {
                    try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                public Object invoke(String action, Object payload) {
                    return "MARKER-" + payload;
                }
            }
            """;

    static String manifest(String id, String version, String apiVersion,
                           String className, String depsJson, String defaultsJson) {
        String name = "\"" + id + "\"";
        String deps = depsJson == null ? "{}" : depsJson;
        String defaults = defaultsJson == null ? "{}" : defaultsJson;
        return """
                {
                  "id": "%s",
                  "name": %s,
                  "version": "%s",
                  "apiVersion": "%s",
                  "className": "%s",
                  "dependencies": %s,
                  "configDefaults": %s
                }
                """.formatted(id, name, version, apiVersion, className, deps, defaults);
    }
}
