package com.github.lechandonga.pluginextensionruntimejava.ext.spring;

import com.github.lechandonga.pluginextensionruntimejava.ext.runtime.ExtensionRuntime;
import com.github.lechandonga.pluginextensionruntimejava.ext.runtime.ReloadResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可选的目录监听：根目录或一级子目录变化后做 500ms 防抖，再调用确定性的 reload()。
 * 默认关闭（extensions.watch=false），保证测试与运行行为可解释。
 */
public class ExtensionDirectoryWatcher {

    private static final Logger log = LoggerFactory.getLogger(ExtensionDirectoryWatcher.class);

    private final ExtensionRuntime runtime;
    private final Path root;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "ext-dir-watcher");
                thread.setDaemon(true);
                return thread;
            });
    private final AtomicLong pendingReloadAt = new AtomicLong();
    private volatile boolean running = true;

    public ExtensionDirectoryWatcher(ExtensionRuntime runtime, Path root) {
        this.runtime = runtime;
        this.root = root;
    }

    public void start() {
        if (root == null || !Files.isDirectory(root)) {
            log.warn("监听未启动：扩展根目录不存在 {}", root);
            return;
        }
        executor.submit(this::watchLoop);
        executor.scheduleWithFixedDelay(this::triggerIfDue, 200, 200, TimeUnit.MILLISECONDS);
    }

    private void watchLoop() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            root.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            while (running) {
                var key = watchService.take();
                key.pollEvents();
                key.reset();
                pendingReloadAt.set(System.currentTimeMillis() + 500);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("扩展目录监听终止: {}", ex.getMessage());
        }
    }

    private void triggerIfDue() {
        long due = pendingReloadAt.get();
        if (due > 0 && System.currentTimeMillis() >= due
                && pendingReloadAt.compareAndSet(due, 0)) {
            ReloadResult result = runtime.reload();
            log.info("目录变更触发热更新：rolledBack={}, updated={}, removed={}, error={}",
                    result.rolledBack(), result.updatedIds(), result.removedIds(), result.errorMessage());
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        executor.shutdownNow();
    }
}
