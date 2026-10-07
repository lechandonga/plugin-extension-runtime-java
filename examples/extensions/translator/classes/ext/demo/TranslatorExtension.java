package ext.demo;

import com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension;
import com.github.lechandonga.pluginextensionruntimejava.ext.spi.ExtensionContext;

/**
 * 示例扩展：声明对 demo-greeter 的版本区间依赖，start 时通过上下文取得依赖扩展。
 */
public class TranslatorExtension implements Extension {

    private Extension greeter;

    @Override
    public void start(ExtensionContext context) {
        this.greeter = context.requireDependency("demo-greeter");
    }

    @Override
    public Object invoke(String action, Object payload) {
        Object upstream = greeter.invoke("compose", payload);
        return "[translated] " + upstream;
    }
}
