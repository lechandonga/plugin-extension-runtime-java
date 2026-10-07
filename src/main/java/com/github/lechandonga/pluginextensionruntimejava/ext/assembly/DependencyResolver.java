package com.github.lechandonga.pluginextensionruntimejava.ext.assembly;

import com.github.lechandonga.pluginextensionruntimejava.ext.discovery.ExtensionScanner.ManifestFailure;
import com.github.lechandonga.pluginextensionruntimejava.ext.discovery.ExtensionScanner.ScannedExtension;
import com.github.lechandonga.pluginextensionruntimejava.ext.assembly.DependencyResolution.RejectedExtension;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.Manifest;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.RejectReason;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.Version;
import com.github.lechandonga.pluginextensionruntimejava.ext.model.VersionRange;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;

/**
 * 依赖装配：扩展 id 去重、接口版本兼容检查、缺失依赖/区间冲突/循环依赖检测、拓扑排序。
 * 任何扩展被拒绝都给出明确原因，绝不静默跳过；带问题的扩展不会进入启动序列。
 */
public class DependencyResolver {

    private final Version supportedApiVersion;

    public DependencyResolver(String supportedApiVersion) {
        this.supportedApiVersion = Version.parse(supportedApiVersion);
    }

    public DependencyResolution resolve(Map<String, ScannedExtension> scanned,
                                        List<ManifestFailure> scanFailures) {
        Map<String, RejectedExtension> rejected = new LinkedHashMap<>();
        return doResolve(scanned, rejected, scanFailures);
    }

    private DependencyResolution doResolve(Map<String, ScannedExtension> scanned,
                                           Map<String, RejectedExtension> rejected,
                                           List<ManifestFailure> scanFailures) {
        if (scanFailures != null) {
            rejected.clear();
            for (ManifestFailure failure : scanFailures) {
                rejected.put(rejectionKey(failure.declaredId(), failure.directory()), new RejectedExtension(
                        failure.declaredId(), null, failure.directory(),
                        failure.error().reason(), failure.error().getMessage()));
            }
        }

        Map<String, ScannedExtension> candidates = new LinkedHashMap<>(scanned);

        // 同 id 重复：保留扫描序中的第一个，其余以 DUPLICATE_ID 拒绝
        Set<String> seenIds = new HashSet<>();
        for (ScannedExtension ext : new ArrayList<>(candidates.values())) {
            if (!seenIds.add(ext.manifest().id())) {
                reject(rejected, candidates, ext, RejectReason.DUPLICATE_ID,
                        "扩展 id '" + ext.manifest().id() + "' 与目录 "
                                + candidates.get(ext.manifest().id()).directory() + " 中的扩展重复");
            }
        }

        // 接口版本兼容：扩展要求的主版本必须等于运行时支持的主版本，次版本不得高于运行时
        for (ScannedExtension ext : new ArrayList<>(candidates.values())) {
            Version required;
            try {
                required = Version.parse(ext.manifest().apiVersion());
            } catch (IllegalArgumentException ex) {
                reject(rejected, candidates, ext, RejectReason.INCOMPATIBLE_API_VERSION,
                        "apiVersion 无法解析: " + ext.manifest().apiVersion());
                continue;
            }
            boolean compatible = required.major() == supportedApiVersion.major()
                    && compareMinorPatch(required, supportedApiVersion) <= 0;
            if (!compatible) {
                reject(rejected, candidates, ext, RejectReason.INCOMPATIBLE_API_VERSION,
                        "扩展要求 apiVersion=" + ext.manifest().apiVersion()
                                + "，运行时支持 apiVersion=" + supportedApiVersion
                                + "（同主版本且扩展次版本不得更高）");
            }
        }

        // 固定点传播：缺失依赖 / 版本区间冲突 / 依赖被拒 会传导到依赖方
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ScannedExtension ext : new ArrayList<>(candidates.values())) {
                Manifest manifest = ext.manifest();
                for (Map.Entry<String, VersionRange> dep : manifest.dependencies().entrySet()) {
                    String depId = dep.getKey();
                    ScannedExtension target = candidates.get(depId);
                    RejectedExtension targetRejection = rejected.get(depId);
                    if (target == null && targetRejection == null) {
                        changed |= reject(rejected, candidates, ext, RejectReason.MISSING_DEPENDENCY,
                                "缺失依赖 '" + depId + "'（要求区间 " + dep.getValue() + "）");
                        break;
                    }
                    if (target == null) {
                        changed |= reject(rejected, candidates, ext, RejectReason.MISSING_DEPENDENCY,
                                "依赖 '" + depId + "' 已被拒绝（原因: "
                                        + targetRejection.reason() + " - " + targetRejection.detail() + "）");
                        break;
                    }
                    if (!dep.getValue().contains(target.manifest().version())) {
                        changed |= reject(rejected, candidates, ext, RejectReason.VERSION_RANGE_CONFLICT,
                                "依赖 '" + depId + "' 实际版本 " + target.manifest().version()
                                        + " 不在声明区间 " + dep.getValue() + " 内");
                        break;
                    }
                }
            }
        }

        // 先通过 Kahn 算法识别循环依赖中的所有节点并拒绝，再对无环节点做拓扑排序
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();
        for (ScannedExtension ext : candidates.values()) {
            String id = ext.manifest().id();
            indegree.putIfAbsent(id, 0);
            dependents.putIfAbsent(id, new ArrayList<>());
        }
        for (ScannedExtension ext : candidates.values()) {
            for (String depId : ext.manifest().dependencies().keySet()) {
                if (candidates.containsKey(depId)) {
                    indegree.merge(ext.manifest().id(), 1, Integer::sum);
                    dependents.get(depId).add(ext.manifest().id());
                }
            }
        }
        java.util.Deque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, deg) -> {
            if (deg == 0) {
                queue.add(id);
            }
        });
        List<String> sortedIds = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            sortedIds.add(id);
            for (String dependent : dependents.get(id)) {
                if (indegree.merge(dependent, -1, Integer::sum) == 0) {
                    queue.add(dependent);
                }
            }
        }

        boolean cycleFound = false;
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() > 0) {
                ScannedExtension member = candidates.get(entry.getKey());
                if (!rejected.containsKey(entry.getKey())) {
                    reject(rejected, candidates, member, RejectReason.CIRCULAR_DEPENDENCY,
                            "扩展 '" + entry.getKey() + "' 位于依赖环中");
                    cycleFound = true;
                }
            }
        }
        if (cycleFound) {
            // 环上节点被拒后会产生新的“依赖被拒”传播，再跑一轮固定点
            return doResolve(candidates, rejected, null);
        }

        List<ScannedExtension> order = new ArrayList<>();
        for (String id : sortedIds) {
            ScannedExtension ext = candidates.get(id);
            if (ext != null && !rejected.containsKey(id)) {
                order.add(ext);
            }
        }
        return new DependencyResolution(order, rejected);
    }

    private static int compareMinorPatch(Version a, Version b) {
        if (a.minor() != b.minor()) {
            return Integer.compare(a.minor(), b.minor());
        }
        return Integer.compare(a.patch(), b.patch());
    }

    private static String rejectionKey(String declaredId, Path directory) {
        return declaredId + "@" + (directory == null ? "?" : directory.getFileName());
    }

    private boolean reject(Map<String, RejectedExtension> rejected,
                           Map<String, ScannedExtension> candidates,
                           ScannedExtension ext, RejectReason reason, String detail) {
        String id = ext.manifest().id();
        if (!rejected.containsKey(id)) {
            rejected.put(id, new RejectedExtension(id, ext.manifest(), ext.directory(), reason, detail));
            candidates.remove(id);
            return true;
        }
        return false;
    }
}
