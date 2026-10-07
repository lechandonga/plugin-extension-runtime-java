package com.github.lechandonga.pluginextensionruntimejava;

import com.github.lechandonga.pluginextensionruntimejava.ext.model.ExtensionStateView;
import com.github.lechandonga.pluginextensionruntimejava.ext.runtime.ExtensionRuntime;
import com.github.lechandonga.pluginextensionruntimejava.ext.runtime.ReloadResult;

import java.nio.file.Path;

/**
 * 不依赖 Spring 的最小演示入口：
 * <pre>
 * mvn -q -DskipTests package
 * java -cp target/classes com.github.lechandonga.pluginextensionruntimejava.ExtensionDemo \
 *      examples/extensions [examples/override.properties]
 * </pre>
 */
public class ExtensionDemo {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("用法: ExtensionDemo <扩展根目录> [覆盖配置文件]");
            System.exit(2);
        }
        Path root = Path.of(args[0]);
        Path override = args.length >= 2 ? Path.of(args[1]) : null;

        ExtensionRuntime runtime = new ExtensionRuntime(root, "1.0", override);
        runtime.start();
        printStates(runtime);

        if (runtime.exists("demo-greeter")) {
            System.out.println("调用 demo-greeter -> " + runtime.invoke("demo-greeter", "greet", "扩展"));
        }
        if (runtime.exists("demo-translator")) {
            System.out.println("调用 demo-translator -> " + runtime.invoke("demo-translator", "translate", "插件"));
        }

        System.out.println("--- 修改扩展目录内容后按回车模拟热更新 ---");
        System.in.read();
        ReloadResult result = runtime.reload();
        System.out.println("热更新结果: rolledBack=" + result.rolledBack()
                + ", updated=" + result.updatedIds()
                + ", removed=" + result.removedIds()
                + ", error=" + result.errorMessage());
        printStates(runtime);
        runtime.shutdown();
    }

    private static void printStates(ExtensionRuntime runtime) {
        for (ExtensionStateView view : runtime.states()) {
            System.out.printf("- %-18s %-9s v%-8s api=%-4s source=%s%n",
                    view.id(), view.status(),
                    view.version() == null ? "-" : view.version(),
                    view.apiVersion() == null ? "-" : view.apiVersion(),
                    view.source());
            if (view.rejectReason() != null) {
                System.out.println("    拒绝原因: " + view.rejectReason() + " :: " + view.rejectDetail());
            }
            if (view.lastRuntimeError() != null) {
                System.out.println("    最近运行期错误(已隔离): " + view.lastRuntimeError());
            }
        }
    }
}
