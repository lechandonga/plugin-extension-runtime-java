package com.github.lechandonga.pluginextensionruntimejava.support;

import com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 测试夹具：在临时目录中构造一个本地扩展（extension.json + classes/ 下的实现源码），
 * 并用当前 JDK 的 JavaCompiler 就地编译，模拟“第三方扩展来源 + 独立类加载器”。
 */
public final class ExtensionFixtures {

    private static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();

    private ExtensionFixtures() {
    }

    public static Path writeExtension(Path extensionsRoot, String dirName, String manifestJson,
                                      String simpleClassName, String javaSource) throws IOException {
        Path dir = Files.createDirectories(extensionsRoot.resolve(dirName));
        Path classesDir = Files.createDirectories(dir.resolve("classes"));
        Files.writeString(dir.resolve("extension.json"), manifestJson, StandardCharsets.UTF_8);
        Path sourceFile = classesDir.resolve(simpleClassName + ".java");
        Files.writeString(sourceFile, javaSource, StandardCharsets.UTF_8);
        compile(classesDir, sourceFile);
        return dir;
    }

    public static void rewriteCompiledClass(Path extensionDir, String simpleClassName,
                                            String javaSource) throws IOException {
        Path classesDir = extensionDir.resolve("classes");
        try (var walk = Files.walk(classesDir)) {
            walk.filter(p -> p.getFileName().toString().endsWith(".class"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
        Files.writeString(classesDir.resolve(simpleClassName + ".java"), javaSource, StandardCharsets.UTF_8);
        try (var walk = Files.walk(classesDir)) {
            walk.filter(p -> p.toString().endsWith(".java")
                            && !p.getFileName().toString().equals(simpleClassName + ".java"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
        compile(classesDir, classesDir.resolve(simpleClassName + ".java"));
    }

    private static void compile(Path outputDir, Path... sources) {
        if (COMPILER == null) {
            throw new IllegalStateException("当前 JDK 缺少 JavaCompiler，请使用 JDK 而非 JRE 运行测试");
        }
        StringWriter errors = new StringWriter();
        String compileClassPath = System.getProperty("java.class.path")
                + java.io.File.pathSeparator
                + codeSourceRoot();
        var fileManager = COMPILER.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        var units = fileManager.getJavaFileObjects(sources);
        Iterable<String> options = java.util.List.of(
                "-d", outputDir.toString(),
                "-classpath", compileClassPath);
        boolean ok = COMPILER.getTask(new PrintWriter(errors), fileManager, null, options, null, units).call();
        if (!ok) {
            throw new IllegalStateException("测试扩展源码编译失败:\n" + errors);
        }
    }

    private static String codeSourceRoot() {
        try {
            return Path.of(Extension.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
