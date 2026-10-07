package ext.demo;

import com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension;
import com.github.lechandonga.pluginextensionruntimejava.ext.spi.ExtensionContext;

/**
 * 示例扩展：读取本扩展命名空间配置 greeting 并拼接问候语。
 * 编译方式见 README.md「本地验证」。
 */
public class GreeterExtension implements Extension {

    private String greeting;

    @Override
    public void start(ExtensionContext context) {
        this.greeting = context.getConfig("greeting", "你好");
    }

    @Override
    public Object invoke(String action, Object payload) {
        return greeting + ", " + payload + "! (action=" + action + ")";
    }
}
