package com.github.lechandonga.pluginextensionruntimejava.ext.discovery;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 每个扩展独立的类加载器：classes/ 目录 + lib/ 下所有 jar。
 * 父加载器是框架的应用类加载器，因此所有扩展共享同一份 SPI 接口定义，
 * 但扩展实现类仅存在于各自的子加载器中，兄弟扩展之间互不可见（实现与版本隔离）。
 */
public class ExtensionClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private ExtensionClassLoader(URL[] urls, ClassLoader parent) {
        super("ext-classloader", urls, parent);
    }

    public static ExtensionClassLoader create(Path extensionDir) throws Exception {
        Path classesDir = extensionDir.resolve("classes");
        if (!Files.isDirectory(classesDir)) {
            throw new IllegalStateException("扩展实现目录不存在: " + classesDir);
        }
        List<URL> urls = new ArrayList<>();
        urls.add(classesDir.toUri().toURL());
        Path libDir = extensionDir.resolve("lib");
        if (Files.isDirectory(libDir)) {
            try (var stream = Files.list(libDir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .sorted()
                        .forEach(p -> {
                            try {
                                urls.add(p.toUri().toURL());
                            } catch (Exception ex) {
                                throw new IllegalStateException(ex);
                            }
                        });
            }
        }
        return new ExtensionClassLoader(urls.toArray(new URL[0]),
                ExtensionClassLoader.class.getClassLoader());
    }
}
