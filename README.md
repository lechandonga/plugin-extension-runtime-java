# 本地插件化扩展框架

基于本地目录中的扩展清单（`extension-manifest.json`）与实现类模拟第三方扩展来源，
提供扩展的发现、生命周期管理、配置注入、依赖装配、热更新与故障隔离能力。

## 目录结构

```
extensions/                          # 扩展根目录（可用配置 extensions.root 修改）
└── echo/                            # 一个扩展 = 一个一级子目录
    ├── extension-manifest.json      # 扩展清单（必需）
    └── config.properties            # 扩展级配置（可选）
```

## 扩展清单格式

```json
{
  "id": "echo",
  "name": "Echo 示例扩展",
  "version": "1.0.0",
  "apiVersion": "1",
  "implementation": "com.github.lechandonga.pluginextensionruntimejava.demo.EchoExtension",
  "dependencies": [
    { "id": "core", "version": "[1.0,2.0)" }
  ],
  "config": {
    "greeting": "hello from manifest"
  }
}
```

- `id`：必填，全局唯一标识。
- `version`：必填，语义化版本 `major[.minor[.patch]]`。
- `apiVersion`：必填，须等于框架当前 API 主版本（当前为 `"1"`）。
- `implementation`：必填，实现 `extension.Extension` 接口的全限定类名，需有无参构造器。
- `dependencies`：可选，声明对其它扩展的依赖。
- `config`：可选，清单内置默认配置（优先级最低）。

框架在 `start(ExtensionContext)` 回调前完成配置注入。

## 配置优先级

每个扩展拥有独立命名空间（以其 `id` 隔离），同一 key 的生效优先级（低 → 高）：

1. 清单内置默认配置 `manifest.config`
2. 扩展目录下的 `config.properties`
3. 外部覆盖：Spring 环境中 `extension.<extensionId>.<key>` 形式的配置
   （如 `application.properties`、命令行 `--extension.echo.greeting=hi`）

## 依赖与版本兼容规则

- **API 版本**：清单 `apiVersion` 必须与框架 `ExtensionManager.CURRENT_API_VERSION` 一致，
  否则以 `INCOMPATIBLE_API_VERSION` 拒绝。
- **版本区间**：依赖的 `version` 支持精确版本（`1.2.0`）与区间写法
  （`[1.0,2.0)`、`(1.0,2.0]`、`[1.0,]`），实际版本不在区间内以 `VERSION_CONFLICT` 拒绝。
- **缺失依赖**：依赖的扩展不存在或自身已被拒绝时，以 `MISSING_DEPENDENCY` 拒绝，不会静默跳过。
- **循环依赖**：拓扑排序阶段检测依赖环，环上所有扩展以 `CYCLIC_DEPENDENCY` 拒绝。
- **拒绝原因可区分**：所有拒绝/失败通过 `ExtensionInfo.rejectionReason` 暴露
  （`INVALID_MANIFEST` / `DUPLICATE_ID` / `INCOMPATIBLE_API_VERSION` /
  `MISSING_DEPENDENCY` / `VERSION_CONFLICT` / `CYCLIC_DEPENDENCY` / `INSTANTIATION_FAILURE`）。
- **失败隔离**：单个扩展被拒绝或启动失败不影响其它扩展的加载与使用。

## 热更新与并发语义

- `ExtensionManager.update(extensionDir)` 在锁内先完整构建并启动新版本，
  再通过 `AtomicReference` 原子替换注册表中的持有者；
  并发读者只会观察到完整的旧版本或完整的新版本，绝不暴露半加载状态。
- 更新过程中任何一步失败（清单非法、依赖冲突、start 抛错等）都会回退：
  旧版本保持 ACTIVE 继续对外服务。
- 运行期通过 `ExtensionManager.invoke(id, action)` 调用扩展，扩展抛错会被隔离记录，
  不扩散到调用方与其它扩展。

## 状态查询

- 编程接口：`ExtensionManager.registry().snapshot()` 返回全部扩展的
  `ExtensionInfo`（id、version、source、state、rejectionReason）。
- HTTP 接口：应用启动后 `GET /extensions` 返回相同快照。

## 本地验证

```bash
# 运行全部单测（日志中包含各业务场景与用例说明）
mvn test

# 启动应用并加载 extensions/ 下的示例扩展
mvn spring-boot:run
curl http://localhost:8080/extensions
```

测试覆盖的业务场景：

| 测试类 | 业务场景 | 用例 |
| --- | --- | --- |
| `ExtensionLoadingTest` | 扩展发现与生命周期 | 清单非法、标识重复、API 版本不兼容、单扩展失败隔离、配置优先级 |
| `DependencyResolutionTest` | 依赖装配 | 缺失依赖、版本区间冲突、循环依赖、依赖链按序装配 |
| `HotUpdateTest` | 热更新与故障隔离 | 更新失败回退、原子切换、并发读写一致性、运行期异常隔离 |
